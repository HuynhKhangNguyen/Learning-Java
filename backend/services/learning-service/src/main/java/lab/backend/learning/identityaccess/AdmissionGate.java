package lab.backend.learning.identityaccess;

import static lab.backend.learning.identityaccess.AdmissionWire.*;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import javax.sql.DataSource;
import lab.backend.learning.transport.WireCodec;

/** Every admission and fence serializes on the same durable account row. */
public final class AdmissionGate {
  private final DataSource source;
  private final UUID realm,generation;
  public AdmissionGate(DataSource source,UUID realm,UUID generation){this.source=source;this.realm=realm;this.generation=generation;}
  @FunctionalInterface public interface Work<T>{T run(Connection connection,UUID principal)throws SQLException;}
  public static void requireOwner(UUID principal,UUID resourceOwner){if(!principal.equals(resourceOwner))throw deny(404,"RESOURCE_NOT_FOUND");}
  public void verifyRuntime(){
    try(var c=source.getConnection()){
      environment(c);time(c);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT current_database(),current_user,has_table_privilege(current_user,'learning.auth_environment','UPDATE'),has_table_privilege(current_user,'learning.auth_session_fence','DELETE'),has_table_privilege(current_user,'learning.auth_command_receipt','UPDATE')")){
        if(!r.next() || !r.getString(1).equals("learning_db") || !r.getString(2).equals("learning_app") || r.getBoolean(3)||r.getBoolean(4)||r.getBoolean(5))throw deny(503,"RECEIVER_NOT_READY");
      }
      try(var s=c.createStatement()){s.executeQuery("SELECT admission_id FROM learning.auth_admission_use LIMIT 0").close();}
    }catch(SQLException e){throw deny(503,"RECEIVER_NOT_READY");}
  }
  <T>T use(Admission a,Request request,Work<T> work){
    if(!a.realm().equals(realm)||!a.context().equals(request.context)||!a.nonce().equals(request.nonce)||!a.binding().equals(request.binding))throw deny(403,"ADMISSION_BINDING_DENIED");
    return transaction(c->{
      environment(c);window(a,time(c),6500);long floor=lock(c,a.user(),a.epoch());
      if(a.epoch()<floor)throw deny(403,"EPOCH_FENCED");
      try(var s=c.prepareStatement("SELECT 1 FROM learning.auth_session_fence WHERE session_ref=? OR context_id=?")){
        s.setObject(1,a.session());s.setObject(2,a.context());try(var r=s.executeQuery()){if(r.next())throw deny(403,"SESSION_FENCED");}
      }
      try(var s=c.prepareStatement("INSERT INTO learning.auth_admission_use(admission_id,user_id,request_binding) VALUES (?,?,?) ON CONFLICT DO NOTHING")){
        s.setObject(1,a.id());s.setObject(2,a.user());s.setString(3,a.binding());if(s.executeUpdate()!=1)throw deny(409,"ADMISSION_ALREADY_USED");
      }
      T result=work.run(c,a.user());
      // Work is local SQL only; check clock/deadline after effect and before commit, with gate still held.
      window(a,time(c),500);environment(c);return result;
    });
  }
  public Ack fence(Fence f){
    if(!f.realm().equals(realm)||!f.generation().equals(generation))throw deny(409,"FENCE_GENERATION_DENIED");
    return transaction(c->{
      environment(c);time(c);long floor=lock(c,f.user(),f.floor());
      try(var s=c.prepareStatement("SELECT payload_digest,command_digest,scope,applied_floor,session_ref,context_id FROM learning.auth_command_receipt WHERE operation_id=?")){
        s.setObject(1,f.operation());try(var r=s.executeQuery()){if(r.next()){
          if(!r.getString(1).equals(f.payloadDigest()))throw deny(409,"COMMAND_CONFLICT");
          return new Ack(f.operation(),r.getString(2),realm,generation,r.getString(3),r.getLong(4),r.getObject(5,UUID.class),r.getObject(6,UUID.class));
        }}
      }
      if(f.scope().equals("SESSION")){
        try(var s=c.prepareStatement("INSERT INTO learning.auth_session_fence(session_ref,context_id,user_id,realm_id,operation_id) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING")){
          s.setObject(1,f.session());s.setObject(2,f.context());s.setObject(3,f.user());s.setObject(4,realm);s.setObject(5,f.operation());s.executeUpdate();
        }
        try(var s=c.prepareStatement("SELECT session_ref,context_id,user_id,realm_id FROM learning.auth_session_fence WHERE session_ref=? OR context_id=?")){
          s.setObject(1,f.session());s.setObject(2,f.context());try(var r=s.executeQuery()){
            if(!r.next()||!f.session().equals(r.getObject(1,UUID.class))||!f.context().equals(r.getObject(2,UUID.class))||!f.user().equals(r.getObject(3,UUID.class))||!realm.equals(r.getObject(4,UUID.class))||r.next())throw deny(409,"SESSION_BINDING_CONFLICT");
          }
        }
      }
      try(var s=c.prepareStatement("INSERT INTO learning.auth_command_receipt(operation_id,command_digest,payload_digest,user_id,realm_id,db_generation,scope,applied_floor,session_ref,context_id) VALUES (?,?,?,?,?,?,?,?,?,?)")){
        s.setObject(1,f.operation());s.setString(2,f.digest());s.setString(3,f.payloadDigest());s.setObject(4,f.user());s.setObject(5,realm);s.setObject(6,generation);s.setString(7,f.scope());s.setLong(8,floor);s.setObject(9,f.session());s.setObject(10,f.context());s.executeUpdate();
      }
      environment(c);return new Ack(f.operation(),f.digest(),realm,generation,f.scope(),floor,f.session(),f.context());
    }); // transaction() returns only after Connection.commit succeeds.
  }
  private long lock(Connection c,UUID user,long epoch)throws SQLException{
    try(var s=c.prepareStatement("INSERT INTO learning.auth_account_gate(user_id,realm_id,min_credential_epoch) VALUES (?,?,?) ON CONFLICT DO NOTHING")){s.setObject(1,user);s.setObject(2,realm);s.setLong(3,epoch);s.executeUpdate();}
    long floor;
    try(var s=c.prepareStatement("SELECT realm_id,min_credential_epoch FROM learning.auth_account_gate WHERE user_id=? FOR UPDATE")){
      s.setObject(1,user);try(var r=s.executeQuery()){if(!r.next()||!realm.equals(r.getObject(1,UUID.class)))throw deny(403,"REALM_DENIED");floor=r.getLong(2);}
    }
    if(epoch>floor){try(var s=c.prepareStatement("UPDATE learning.auth_account_gate SET min_credential_epoch=? WHERE user_id=?")){s.setLong(1,epoch);s.setObject(2,user);s.executeUpdate();}floor=epoch;}return floor;
  }
  private void environment(Connection c)throws SQLException{
    // Shared lock prevents an operator readiness change racing protected commits.
    try(var s=c.createStatement();var r=s.executeQuery("SELECT realm_id,db_generation,topology_version,ready FROM learning.auth_environment WHERE singleton FOR SHARE")){
      if(!r.next()||!realm.equals(r.getObject(1,UUID.class))||!generation.equals(r.getObject(2,UUID.class))||!TOPOLOGY.equals(r.getString(3))||!r.getBoolean(4))throw deny(503,"RECEIVER_NOT_READY");
    }
  }
  private static Instant time(Connection c)throws SQLException{
    Instant before=Instant.now();long start=System.nanoTime();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT clock_timestamp()")){
      r.next();Instant db=r.getObject(1,OffsetDateTime.class).toInstant(),after=Instant.now();
      if(after.isBefore(before)||System.nanoTime()-start>1_000_000_000L||db.isBefore(before.minusMillis(500))||db.isAfter(after.plusMillis(500)))throw deny(503,"CLOCK_UNAVAILABLE");return db;
    }
  }
  private static void window(Admission a,Instant now,long reserve){if(!a.until().isAfter(now.plusMillis(reserve))||!a.idle().isAfter(now.plusMillis(reserve))||!a.absolute().isAfter(now.plusMillis(reserve)))throw deny(403,"ADMISSION_EXPIRED");}
  private <T>T transaction(Sql<T> work){
    try(var c=source.getConnection()){
      c.setAutoCommit(false);
      try{
        try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='5000ms'; SET LOCAL statement_timeout='5000ms'; SET LOCAL transaction_timeout='6000ms'");}
        T value=work.run(c);c.commit();return value;
      }catch(Exception failure){try{c.rollback();}catch(SQLException ignored){}if(failure instanceof WireCodec.Denied d)throw d;if(failure instanceof SQLException s && "23505".equals(s.getSQLState()))throw deny(409,"COMMAND_CONFLICT");throw deny(503,"RECEIVER_UNAVAILABLE");}
    }catch(SQLException e){throw deny(503,"RECEIVER_UNAVAILABLE");}
  }
  @FunctionalInterface private interface Sql<T>{T run(Connection c)throws SQLException;}
}
