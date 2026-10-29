package lab.backend.identity.credential;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

/** DB-clock buckets. Reservations bound concurrent attempts before KDF. */
public final class AuthThrottle {
  // REAUTH tách bucket failed-password khỏi login ACCOUNT, nhưng vẫn cùng IP cap.
  public enum Kind { ACCOUNT(5), IP(30), ACTIVATE(5), RESET(5), REAUTH(5); final int limit; Kind(int limit){this.limit=limit;} }
  public enum Decision { ALLOW, BLOCKED, CAPACITY_REJECTED }
  public record Key(Kind kind, String value) {
    @Override public String toString() { return "ThrottleKey["+kind+",REDACTED]"; }
  }
  private final DataSource dataSource;
  private final byte[] hmacKey;
  private final int capacity;
  public AuthThrottle(DataSource dataSource, byte[] hmacKey, int capacity) {
    if (hmacKey==null || hmacKey.length<32 || capacity<1 || capacity>100000)
      throw new IllegalArgumentException("Invalid throttle configuration");
    this.dataSource=dataSource; this.hmacKey=hmacKey.clone(); this.capacity=capacity;
  }
  public Decision beforeKdf(Kind kind,String raw) throws SQLException { return reserve(List.of(new Key(kind,raw))); }
  public Decision reserve(List<Key> keys) throws SQLException {
    try (Connection c=transaction()) {
      try {
        purge(c);
        for (Key key:keys) {
          byte[] digest=digest(key.kind,key.value);
          if (!exists(c,key.kind,digest)) {
            if (count(c)>=capacity) { c.rollback(); return Decision.CAPACITY_REJECTED; }
            insert(c,key.kind,digest);
          }
          try (var s=c.prepareStatement("UPDATE identity.auth_throttle SET failed_count=CASE WHEN window_started_at<=clock_timestamp()-interval '15 minutes' THEN 0 ELSE failed_count END,window_started_at=CASE WHEN window_started_at<=clock_timestamp()-interval '15 minutes' THEN clock_timestamp() ELSE window_started_at END,blocked_until=CASE WHEN blocked_until<=clock_timestamp() THEN NULL ELSE blocked_until END WHERE bucket_kind=? AND key_digest=?")) {
            s.setString(1,key.kind.name());s.setBytes(2,digest);s.executeUpdate();
          }
          try (var s=c.prepareStatement("UPDATE identity.auth_throttle SET in_flight=in_flight+1,expires_at=clock_timestamp()+interval '30 minutes' WHERE bucket_kind=? AND key_digest=? AND (blocked_until IS NULL OR blocked_until<=clock_timestamp()) AND failed_count+in_flight<?")) {
            s.setString(1,key.kind.name());s.setBytes(2,digest);s.setInt(3,key.kind.limit);
            if(s.executeUpdate()!=1) { c.rollback(); return Decision.BLOCKED; }
          }
        }
        c.commit(); return Decision.ALLOW;
      } catch(SQLException|RuntimeException e) {c.rollback();throw e;}
    }
  }
  public void complete(List<Key> keys, boolean failed) throws SQLException {
    try(Connection c=transaction()) {
      try {
        for(Key key:keys) {
          try(var s=c.prepareStatement("UPDATE identity.auth_throttle SET in_flight=GREATEST(in_flight-1,0),failed_count=LEAST(failed_count+?,30),blocked_until=CASE WHEN failed_count+?>=? THEN COALESCE(blocked_until,clock_timestamp()+interval '15 minutes') ELSE blocked_until END,expires_at=clock_timestamp()+interval '30 minutes' WHERE bucket_kind=? AND key_digest=?")) {
            int increment=failed?1:0;
            s.setInt(1,increment);s.setInt(2,increment);s.setInt(3,key.kind.limit);s.setString(4,key.kind.name());s.setBytes(5,digest(key.kind,key.value));s.executeUpdate();
          }
        }
        c.commit();
      } catch(SQLException|RuntimeException e) {c.rollback();throw e;}
    }
  }
  public Decision recordFailure(Kind kind,String raw) throws SQLException {
    Decision admission=beforeKdf(kind,raw);
    if(admission!=Decision.ALLOW)return admission;
    complete(List.of(new Key(kind,raw)),true);
    try(Connection c=dataSource.getConnection();var s=c.prepareStatement("SELECT blocked_until>clock_timestamp() FROM identity.auth_throttle WHERE bucket_kind=? AND key_digest=?")) {
      s.setString(1,kind.name());s.setBytes(2,digest(kind,raw));
      try(var r=s.executeQuery()){r.next();return r.getBoolean(1)?Decision.BLOCKED:Decision.ALLOW;}
    }
  }
  private Connection transaction() throws SQLException {
    Connection c=dataSource.getConnection();
    try {
      c.setAutoCommit(false);
      try(var s=c.createStatement()) {
        s.execute("SET LOCAL lock_timeout='2s'");s.execute("SET LOCAL statement_timeout='5s'");
        s.execute("SELECT pg_advisory_xact_lock(7303,1)");
      }
      return c;
    } catch(SQLException | RuntimeException failure) {c.close();throw failure;}
  }
  private void purge(Connection c) throws SQLException {
    try(var s=c.prepareStatement("DELETE FROM identity.auth_throttle WHERE expires_at<=clock_timestamp()")){s.executeUpdate();}
  }
  private int count(Connection c) throws SQLException {
    try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM identity.auth_throttle")){r.next();return r.getInt(1);}
  }
  private boolean exists(Connection c,Kind kind,byte[] digest) throws SQLException {
    try(var s=c.prepareStatement("SELECT 1 FROM identity.auth_throttle WHERE bucket_kind=? AND key_digest=?")){s.setString(1,kind.name());s.setBytes(2,digest);try(var r=s.executeQuery()){return r.next();}}
  }
  private void insert(Connection c,Kind kind,byte[] digest) throws SQLException {
    try(var s=c.prepareStatement("INSERT INTO identity.auth_throttle(key_digest,bucket_kind,window_started_at,failed_count,expires_at) VALUES (?,?,clock_timestamp(),0,clock_timestamp()+interval '30 minutes')")){s.setBytes(1,digest);s.setString(2,kind.name());s.executeUpdate();}
  }
  byte[] digest(Kind kind,String raw) {
    if(raw==null || raw.length()>256)throw new IllegalArgumentException("Invalid throttle input");
    try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hmacKey,"HmacSHA256"));return mac.doFinal((kind.name()+"\0"+raw).getBytes(StandardCharsets.UTF_8));}
    catch(Exception e){throw new IllegalStateException("HMAC unavailable");}
  }
}
