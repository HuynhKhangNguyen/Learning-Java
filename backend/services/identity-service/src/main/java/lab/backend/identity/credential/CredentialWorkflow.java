package lab.backend.identity.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.sql.DataSource;

/** Local credential authority; app_user is the account lock for future R04 session work. */
public final class CredentialWorkflow {
  public enum Purpose { ACTIVATE, RESET }
  public enum AccountState { INVITED, ACTIVE, DISABLED, RECOVERY_REQUIRED }
  public record IssuedToken(UUID userId, Purpose purpose, String rawToken) {
    @Override public String toString() { return "IssuedToken[userId=" + userId + ",purpose=" + purpose + ",token=REDACTED]"; }
  }
  record Account(UUID user, String state, String hash, long epoch, boolean pending, boolean disabled) {}
  /** Joins the credential transaction after the account/token locked recheck.
   * The callback must not commit, do KDF/network I/O or acquire an account lock. */
  @FunctionalInterface public interface ConsumedAction {
    void apply(Connection transaction, UUID user, long epoch, UUID transitionId) throws SQLException;
  }
  private final DataSource dataSource;
  private final PasswordHasher hasher;
  private final SecureRandom random = new SecureRandom();

  public CredentialWorkflow(DataSource dataSource) { this(dataSource, new Argon2CredentialHasher()); }
  CredentialWorkflow(DataSource dataSource, PasswordHasher hasher) {
    this.dataSource = dataSource;
    this.hasher = hasher;
  }

  public IssuedToken invite(String suppliedLoginName) throws SQLException {
    String login = normalizeLoginName(suppliedLoginName);
    UUID user = UUID.randomUUID();
    try (Connection c = transaction()) {
      try {
        try (var s = c.prepareStatement("INSERT INTO identity.app_user(user_id,login_name,state) VALUES (?,?,'INVITED')")) {
          s.setObject(1,user); s.setString(2,login); s.executeUpdate();
        }
        IssuedToken token = issue(c,user,Purpose.ACTIVATE,0,Duration.ofHours(24));
        audit(c,"INVITE_CREATED",user,"SUCCEEDED","INVITED");
        c.commit(); return token;
      } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
    }
  }

  public IssuedToken reissue(UUID user, Purpose purpose) throws SQLException {
    try (Connection c = transaction()) {
      try {
        Account account = lockUser(c,user);
        boolean allowed = purpose == Purpose.ACTIVATE ? account.state.equals("INVITED")
            : account.state.equals("ACTIVE") || account.state.equals("RECOVERY_REQUIRED");
        if (!allowed || account.disabled) throw new IllegalArgumentException("Token cannot be issued for current account state");
        invalidate(c,user,purpose);
        IssuedToken token = issue(c,user,purpose,account.epoch,
            purpose == Purpose.ACTIVATE ? Duration.ofHours(24) : Duration.ofMinutes(15));
        audit(c,purpose + "_REISSUED",user,"SUCCEEDED","TOKEN_REISSUED");
        c.commit(); return token;
      } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
    }
  }

  public boolean consume(Purpose purpose, String rawToken, String newPassword, PasswordCorpus corpus) throws SQLException {
    return consume(purpose,rawToken,newPassword,corpus,(c,user,epoch,transitionId)->{});
  }

  public boolean consume(Purpose purpose, String rawToken, String newPassword, PasswordCorpus corpus,
      ConsumedAction action) throws SQLException {
    PasswordPolicy.requireValid(newPassword);
    corpus.requireAllowed(newPassword);
    byte[] tokenDigest = digestToken(rawToken);
    if (tokenDigest == null) return false;
    // Lookup only. Eligibility is rechecked after KDF under the account lock,
    // always before any token lock, matching the maintenance lock order.
    UUID user;
    try (Connection c = dataSource.getConnection(); var s = c.prepareStatement(
        "SELECT user_id FROM identity.credential_token WHERE token_digest=? AND purpose=?")) {
      s.setBytes(1,tokenDigest); s.setString(2,purpose.name());
      try (var r = s.executeQuery()) { if (!r.next()) return false; user = r.getObject(1,UUID.class); }
    }
    String encoded = hasher.hash(newPassword);
    try (Connection c = transaction()) {
      try {
        Account account = lockUser(c,user);
        boolean eligible = purpose == Purpose.ACTIVATE ? account.state.equals("INVITED")
            : account.state.equals("ACTIVE") || account.state.equals("RECOVERY_REQUIRED");
        if (!eligible || account.disabled) { c.rollback(); return false; }
        try (var s = c.prepareStatement("SELECT token_id FROM identity.credential_token WHERE token_digest=? AND purpose=? AND user_id=? AND issued_epoch=? AND consumed_at IS NULL AND invalidated_at IS NULL AND expires_at>clock_timestamp() FOR UPDATE")) {
          s.setBytes(1,tokenDigest); s.setString(2,purpose.name()); s.setObject(3,user); s.setLong(4,account.epoch);
          try (var r = s.executeQuery()) { if (!r.next()) { c.rollback(); return false; } }
        }
        try (var s = c.prepareStatement("UPDATE identity.credential_token SET consumed_at=clock_timestamp() WHERE token_digest=? AND expires_at>clock_timestamp() AND consumed_at IS NULL AND invalidated_at IS NULL")) {
          s.setBytes(1,tokenDigest);
          if (s.executeUpdate()!=1) { c.rollback(); return false; }
        }
        boolean transition = purpose == Purpose.RESET;
        try (var s = c.prepareStatement("UPDATE identity.app_user SET password_hash=?,state='ACTIVE',credential_epoch=credential_epoch+1,auth_transition_pending=?,updated_at=clock_timestamp() WHERE user_id=?")) {
          s.setString(1,encoded); s.setBoolean(2,transition); s.setObject(3,user); s.executeUpdate();
        }
        invalidate(c,user,null);
        UUID transitionId=transition ? transition(c,user,account.epoch+1,"RESET") : null;
        action.apply(c,user,account.epoch+1,transitionId);
        audit(c,purpose + "_CONSUMED",user,"SUCCEEDED","CREDENTIAL_EPOCH_ADVANCED");
        c.commit(); return true;
      } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
    }
  }

  public void setState(UUID user, AccountState state) throws SQLException {
    if (state == AccountState.INVITED) throw new IllegalArgumentException("Cannot return an account to INVITED");
    try (Connection c = transaction()) {
      try {
        Account previous = lockUser(c,user);
        if (previous.state.equals("INVITED"))
          throw new IllegalArgumentException("An invited account must activate before state maintenance");
        String next=state.name();
        boolean disabled=previous.disabled;
        if (state == AccountState.ACTIVE) {
          if (!previous.disabled) throw new IllegalArgumentException("Enable requires an operator-disabled account");
          // Clearing an operator disable must never clear the independent recovery gate.
          if (previous.state.equals("RECOVERY_REQUIRED")) next="RECOVERY_REQUIRED";
          else if (!previous.state.equals("DISABLED") || previous.hash==null)
            throw new IllegalArgumentException("Recovery requires a fresh reset");
          disabled=false;
        } else if (state == AccountState.DISABLED) {
          if (previous.state.equals("RECOVERY_REQUIRED")) next="RECOVERY_REQUIRED";
          disabled=true;
        }
        if (previous.state.equals(next) && previous.disabled==disabled) { c.commit(); return; }
        try (var s = c.prepareStatement("UPDATE identity.app_user SET state=?,operator_disabled=?,password_hash=CASE WHEN ?='RECOVERY_REQUIRED' THEN NULL ELSE password_hash END,credential_epoch=credential_epoch+1,auth_transition_pending=true,updated_at=clock_timestamp() WHERE user_id=?")) {
          s.setString(1,next); s.setBoolean(2,disabled); s.setString(3,next); s.setObject(4,user); s.executeUpdate();
        }
        invalidate(c,user,null);
        transition(c,user,previous.epoch+1,state.name());
        audit(c,"ACCOUNT_STATE_CHANGED",user,"SUCCEEDED",state.name());
        c.commit();
      } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
    }
  }

  public static String normalizeLoginName(String value) {
    if (value == null || value.length()>256) throw new IllegalArgumentException("Invalid login name");
    String input=value.trim();
    StringBuilder normalized=new StringBuilder();
    for (char ch : input.toCharArray()) {
      if (ch>127) throw new IllegalArgumentException("Invalid login name");
      normalized.append(ch>='A' && ch<='Z' ? (char)(ch+32) : ch);
    }
    String result=normalized.toString();
    if (!result.matches("[a-z0-9][a-z0-9._-]{2,31}")) throw new IllegalArgumentException("Invalid login name");
    return result;
  }

  private Connection transaction() throws SQLException {
    Connection c = dataSource.getConnection();
    try {
      c.setAutoCommit(false);
      try (var s=c.createStatement()) { s.execute("SET LOCAL lock_timeout='2s'"); s.execute("SET LOCAL statement_timeout='5s'"); }
      return c;
    } catch(SQLException | RuntimeException failure) {c.close();throw failure;}
  }
  private IssuedToken issue(Connection c, UUID user, Purpose purpose, long epoch, Duration ttl) throws SQLException {
    byte[] bytes=new byte[32]; random.nextBytes(bytes);
    String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try (var s=c.prepareStatement("INSERT INTO identity.credential_token(token_id,user_id,purpose,token_digest,issued_epoch,expires_at) VALUES (?,?,?,?,?,clock_timestamp()+(? * interval '1 second'))")) {
      s.setObject(1,UUID.randomUUID()); s.setObject(2,user); s.setString(3,purpose.name()); s.setBytes(4,digestToken(raw)); s.setLong(5,epoch); s.setLong(6,ttl.toSeconds()); s.executeUpdate();
    }
    return new IssuedToken(user,purpose,raw);
  }
  static Account lockUser(Connection c, UUID user) throws SQLException {
    try (var s=c.prepareStatement("SELECT state,password_hash,credential_epoch,auth_transition_pending,operator_disabled FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
      s.setObject(1,user);
      try (var r=s.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Unknown account");
        return new Account(user,r.getString(1),r.getString(2),r.getLong(3),r.getBoolean(4),r.getBoolean(5));
      }
    }
  }
  private static void invalidate(Connection c, UUID user, Purpose purpose) throws SQLException {
    String sql="UPDATE identity.credential_token SET invalidated_at=clock_timestamp() WHERE user_id=? AND consumed_at IS NULL AND invalidated_at IS NULL" + (purpose==null ? "" : " AND purpose=?");
    try (var s=c.prepareStatement(sql)) { s.setObject(1,user); if(purpose!=null)s.setString(2,purpose.name()); s.executeUpdate(); }
  }
  private static UUID transition(Connection c, UUID user, long epoch, String action) throws SQLException {
    UUID operation=UUID.randomUUID();
    try (var s=c.prepareStatement("INSERT INTO identity.credential_transition(operation_id,user_id,credential_epoch,action) VALUES (?,?,?,?)")) {
      s.setObject(1,operation); s.setObject(2,user); s.setLong(3,epoch); s.setString(4,action); s.executeUpdate();
    }
    return operation;
  }
  static void audit(Connection c,String event,UUID user,String result,String detail) throws SQLException {
    try (var s=c.prepareStatement("INSERT INTO identity.security_audit(audit_id,event_type,target_user_id,result,correlation_id,detail_code) VALUES (?,?,?,?,?,?)")) {
      s.setObject(1,UUID.randomUUID()); s.setString(2,event); s.setObject(3,user); s.setString(4,result); s.setObject(5,UUID.randomUUID()); s.setString(6,detail); s.executeUpdate();
    }
  }
  private static byte[] digestToken(String value) {
    if (value==null || !value.matches("[A-Za-z0-9_-]{43}")) return null;
    try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
    catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable"); }
  }
}
