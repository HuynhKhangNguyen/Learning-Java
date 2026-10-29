package lab.backend.identity.session;

import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lab.backend.identity.delivery.AuthDeliveryStore;
import lab.backend.identity.delivery.AuthDeliveryWire;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;

/**
 * Nối canonical deny/immutable intents với independent auth outbox ở own Identity DB.
 * Không gửi HTTP/KDF dưới lock; dispatcher lưu durable ACK trước, coordinator chỉ
 * đọc lại hai ACK hiện hành rồi đóng auth barrier. Không là lifecycle journal owner.
 */
public final class AuthBarrierCoordinator {
  private final DataSource source;
  private final UUID realm,generation;
  private final Map<String,UUID> peers;
  private final AuthDeliveryStore deliveries;
  private record Operation(UUID id,UUID user,UUID session,UUID context,long floor,String scope,
      String action,String digest,String state) {}

  public AuthBarrierCoordinator(DataSource source,UUID realm,UUID generation,Map<String,UUID> peers) {
    this.source=Objects.requireNonNull(source);this.realm=Objects.requireNonNull(realm);
    this.generation=Objects.requireNonNull(generation);this.peers=Map.copyOf(peers);
    deliveries=new AuthDeliveryStore(source,realm,generation,peers);
  }
  public AuthDeliveryStore deliveries(){return deliveries;}
  /** Entry nội bộ feature cho protected reauth: gọi sau account/session locks,
   * giữ current realm/generation/readiness tới commit, không auto-provision. */
  void requireReady(Connection transaction) throws SQLException {environment(transaction);}
  @Override public String toString(){return "AuthBarrierCoordinator[ownIdentity,configuration=REDACTED]";}

  /** Startup chỉ kiểm schema/role/config, không migration hoặc reconcile implicit. */
  public void verifyRuntime() throws SQLException {
    try(Connection c=transaction()) {
      environment(c);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O' AND (tgrelid,tgname) IN (('identity.auth_operation'::regclass,'auth_operation_completion_guard'),('identity.app_user'::regclass,'auth_pending_clear_guard'),('identity.credential_transition'::regclass,'credential_transition_history_guard'))")) {
        if(!r.next()||r.getInt(1)!=3)throw new Denied(503,"AUTH_BARRIER_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT has_column_privilege(current_user,'identity.auth_operation','state','UPDATE') AND has_column_privilege(current_user,'identity.auth_operation','completed_at','UPDATE') AND NOT has_table_privilege(current_user,'identity.auth_operation','UPDATE,DELETE,TRUNCATE') AND NOT has_table_privilege(current_user,'identity.credential_transition','UPDATE,DELETE,TRUNCATE')")) {
        if(!r.next()||!r.getBoolean(1))throw new Denied(503,"AUTH_BARRIER_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement()) {
        s.executeQuery("SELECT auth_pending_operation FROM identity.app_user LIMIT 0").close();
        s.executeQuery("SELECT initiator_kind,completed_at FROM identity.auth_operation LIMIT 0").close();
      }
      c.commit();
    }
  }

  /**
   * Caller đang sở hữu transaction và account→session locks của canonical effect.
   * Hai exact messages commit cùng deny/operation/intents hoặc rollback tất cả.
   * Retry dùng bytes đã lưu; không đổi generation hay retarget một operation cũ.
   */
  public void append(Connection c,UUID id) throws SQLException {
    if(c.getAutoCommit())throw new SQLException("Auth barrier append requires caller-owned transaction");
    environment(c);Operation op=operation(c,id,false);
    if(!op.state.equals("PENDING_FENCES"))throw new Denied(409,"AUTH_OPERATION_TERMINAL");
    try(var s=c.prepareStatement("SELECT participant,encode(command_digest,'hex') FROM identity.auth_fence_intent WHERE operation_id=? ORDER BY participant")) {
      s.setObject(1,id);try(var r=s.executeQuery()) {
        for(String peer:List.of("content","learning"))
          if(!r.next()||!peer.equals(r.getString(1))||!op.digest.equals(r.getString(2)))throw new Denied(503,"AUTH_INTENTS_INCOMPLETE");
        if(r.next())throw new Denied(503,"AUTH_INTENTS_INCOMPLETE");
      }
    }
    for(String peer:List.of("content","learning")) {
      byte[] bytes=null;
      try(var s=c.prepareStatement("SELECT command_bytes FROM identity.auth_delivery_outbox WHERE operation_id=? AND consumer=?")) {
        s.setObject(1,id);s.setString(2,peer);try(var r=s.executeQuery()){if(r.next())bytes=r.getBytes(1);}
      }
      if(bytes==null)bytes=command(op,peer);
      bound(AuthDeliveryWire.command(bytes),op,peer);
      deliveries.append(c,bytes);
    }
    if(op.scope.equals("ACCOUNT"))try(var s=c.prepareStatement("UPDATE identity.app_user SET auth_pending_operation=? WHERE user_id=? AND credential_epoch=? AND auth_transition_pending AND auth_pending_operation IS DISTINCT FROM ?")) {
      // Unique account+epoch operation và owner FK ngăn thay marker cùng epoch bằng
      // operation khác. Older reconciliation không được đổi marker epoch hiện tại.
      s.setObject(1,id);s.setObject(2,op.user);s.setLong(3,op.floor);s.setObject(4,id);s.executeUpdate();
    }
  }

  /** Reconcile operation legacy đã có deny/intents, không credential consume lần hai. */
  public void reconcile(UUID id) throws SQLException {
    try(Connection c=transaction()) {
      try {Operation op=locked(c,id);if(op.state.equals("PENDING_FENCES"))append(c,id);else environment(c);c.commit();}
      catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
  }

  /**
   * Materialize pending R03 maintenance/legacy reset intent bằng immutable ID/epoch.
   * Không consume token/đổi password hoặc tăng epoch lần nữa. Account đã deny bởi
   * credential workflow; coordinator khóa account→sessions rồi ghi deny/op/intents/
   * hai exact auth deliveries cùng transaction. Maintenance không có browser binding.
   */
  public void reconcileTransition(UUID id) throws SQLException {
    try(Connection c=transaction()) {
      try {
        UUID user;
        try(var s=c.prepareStatement("SELECT user_id FROM identity.credential_transition WHERE operation_id=?")) {
          s.setObject(1,id);try(var r=s.executeQuery()){if(!r.next())throw new Denied(404,"AUTH_TRANSITION_NOT_FOUND");user=r.getObject(1,UUID.class);}
        }
        try(var s=c.prepareStatement("SELECT user_id FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
          s.setObject(1,user);s.executeQuery().close();
        }
        try(var s=c.prepareStatement("SELECT session_ref FROM identity.auth_session WHERE user_id=? AND realm_id=? ORDER BY session_ref FOR UPDATE")) {
          s.setObject(1,user);s.setObject(2,realm);s.executeQuery().close();
        }
        long floor;String action,state;
        try(var s=c.prepareStatement("SELECT credential_epoch,action,state FROM identity.credential_transition WHERE operation_id=? AND user_id=? FOR UPDATE")) {
          s.setObject(1,id);s.setObject(2,user);try(var r=s.executeQuery()) {
            if(!r.next())throw new Denied(409,"AUTH_TRANSITION_CONFLICT");floor=r.getLong(1);action=r.getString(2);state=r.getString(3);
          }
        }
        environment(c);boolean exists;
        try(var s=c.prepareStatement("SELECT 1 FROM identity.auth_operation WHERE operation_id=?")) {
          s.setObject(1,id);try(var r=s.executeQuery()){exists=r.next();}
        }
        if(!exists) {
          if(!state.equals("PENDING"))throw new Denied(503,"AUTH_TRANSITION_HISTORY_INCOMPLETE");
          String commandAction=switch(action){case "RESET"->"RESET";case "DISABLED"->"DISABLE";case "ACTIVE"->"ENABLE";case "RECOVERY_REQUIRED"->"RECOVERY";default->throw new Denied(400,"AUTH_TRANSITION_UNSUPPORTED");};
          String digest=sha(commandAction+"\n"+user+"\nACCOUNT\n"+floor+"\n"+realm+"\n"+AuthDeliveryWire.TOPOLOGY+"\ncontent,learning");
          try(var s=c.prepareStatement("INSERT INTO identity.auth_operation(operation_id,idempotency_key,target_user_id,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants,initiator_kind) VALUES (?,?,?,?,?,'ACCOUNT',?,decode(?,'hex'),'r1-static-v1',ARRAY['content','learning'],'MAINTENANCE')")) {
            s.setObject(1,id);s.setObject(2,id);s.setObject(3,user);s.setLong(4,floor);s.setObject(5,realm);s.setString(6,commandAction);s.setString(7,digest);s.executeUpdate();
          }
          for(String peer:List.of("content","learning"))try(var s=c.prepareStatement("INSERT INTO identity.auth_fence_intent(operation_id,participant,command_digest) VALUES (?,?,decode(?,'hex'))")) {
            s.setObject(1,id);s.setString(2,peer);s.setString(3,digest);s.executeUpdate();
          }
        }
        Operation op=operation(c,id,true);
        if(!op.user.equals(user)||op.floor!=floor)throw new Denied(409,"AUTH_TRANSITION_CONFLICT");
        if(op.state.equals("PENDING_FENCES")) {
          // Framework backing bị xóa cùng local deny, không network dưới locks.
          try(var s=c.prepareStatement("DELETE FROM identity.spring_session WHERE session_id IN (SELECT framework_id FROM identity.auth_session WHERE user_id=? AND realm_id=? AND credential_epoch<=? AND state='ACTIVE')")) {
            s.setObject(1,user);s.setObject(2,realm);s.setLong(3,floor);s.executeUpdate();
          }
          try(var s=c.prepareStatement("UPDATE identity.auth_session SET state='REVOKING',csrf_token=NULL WHERE user_id=? AND realm_id=? AND credential_epoch<=? AND state='ACTIVE'")) {
            s.setObject(1,user);s.setObject(2,realm);s.setLong(3,floor);s.executeUpdate();
          }
          append(c,id);
        }
        c.commit();
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
  }

  /**
   * false nghĩa mandatory ACK còn thiếu/paused, không phải timeout thành success.
   * Lock account→sessions UUID order→operation→environment→outboxes. Generation
   * restore/lease/network retry không được biến receipt cũ thành current completion.
   */
  public boolean complete(UUID id) throws SQLException {
    try(Connection c=transaction()) {
      try {
        Operation op=locked(c,id);environment(c);int count=0;boolean ready=true;
        try(var s=c.prepareStatement("SELECT consumer,command_bytes,ack_bytes,delivery_status FROM identity.auth_delivery_outbox WHERE operation_id=? ORDER BY consumer FOR SHARE")) {
          s.setObject(1,id);try(var r=s.executeQuery()){while(r.next()) {
            String peer=r.getString(1);var command=AuthDeliveryWire.command(r.getBytes(2));bound(command,op,peer);count++;
            byte[] ack=r.getBytes(3);
            if(!r.getString(4).equals("DELIVERED")||ack==null)ready=false;
            else AuthDeliveryWire.ack(ack,command);
          }}
        }
        if(count!=2||!ready){c.commit();return false;}
        if(op.state.equals("COMPLETED")){c.commit();return true;}
        // Clock được kiểm lại sau chờ locks. SQL trigger kiểm cả canonical deny,
        // exact command/ACK/current generation trước khi cho pending→completed.
        environment(c);
        try(var s=c.prepareStatement("UPDATE identity.auth_operation SET state='COMPLETED',completed_at=clock_timestamp() WHERE operation_id=? AND state='PENDING_FENCES'")) {
          s.setObject(1,id);if(s.executeUpdate()!=1)throw new Denied(409,"AUTH_OPERATION_CONFLICT");
        }
        if(op.scope.equals("SESSION"))try(var s=c.prepareStatement("UPDATE identity.auth_session SET state='REVOKED',csrf_token=NULL WHERE session_ref=? AND state<>'ACTIVE'")) {
          s.setObject(1,op.session);s.executeUpdate();
        }
        else {
          try(var s=c.prepareStatement("UPDATE identity.auth_session SET state='REVOKED',csrf_token=NULL WHERE user_id=? AND realm_id=? AND credential_epoch<=? AND state IN ('REVOKING','EXPIRED')")) {
            s.setObject(1,op.user);s.setObject(2,realm);s.setLong(3,op.floor);s.executeUpdate();
          }
          // Reset mới cũng không được bypass journal pending của disable/enable
          // trước đó. Các credential_transition này chỉ owner R29b mới đóng bằng
          // journal proof; hai auth ACK không thay bằng chứng đó.
          if(Set.of("RESET","LOGOUT_ALL","PASSWORD_CHANGE").contains(op.action))try(var s=c.prepareStatement("UPDATE identity.app_user u SET auth_transition_pending=false,auth_pending_operation=NULL WHERE user_id=? AND credential_epoch=? AND auth_pending_operation=? AND auth_transition_pending AND state='ACTIVE' AND NOT operator_disabled AND NOT EXISTS(SELECT 1 FROM identity.credential_transition t WHERE t.user_id=u.user_id AND t.state='PENDING' AND t.action IN ('DISABLED','ACTIVE'))")) {
            s.setObject(1,op.user);s.setLong(2,op.floor);s.setObject(3,id);s.executeUpdate();
          }
          // Disable/enable/recovery giữ lifecycle pending; auth ACK không journal ACK.
          if(op.action.equals("RESET"))try(var s=c.prepareStatement("UPDATE identity.credential_transition SET state='COMPLETED' WHERE operation_id=? AND user_id=? AND credential_epoch=? AND state='PENDING'")) {
            s.setObject(1,id);s.setObject(2,op.user);s.setLong(3,op.floor);s.executeUpdate();
          }
        }
        try(var s=c.prepareStatement("INSERT INTO identity.security_audit(audit_id,event_type,target_user_id,result,correlation_id,detail_code) VALUES (?,'AUTH_BARRIER_COMPLETED',?,'SUCCEEDED',?,'MANDATORY_AUTH_FENCES_COMPLETE')")) {
          s.setObject(1,UUID.randomUUID());s.setObject(2,op.user);s.setObject(3,id);s.executeUpdate();
        }
        c.commit();return true;
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
  }

  private Operation locked(Connection c,UUID id) throws SQLException {
    Operation initial=operation(c,id,false);
    try(var s=c.prepareStatement("SELECT user_id FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
      s.setObject(1,initial.user);try(var r=s.executeQuery()){if(!r.next())throw new Denied(503,"AUTH_ACCOUNT_UNAVAILABLE");}
    }
    String sql=initial.scope.equals("SESSION")?"SELECT session_ref FROM identity.auth_session WHERE session_ref=? FOR UPDATE"
        :"SELECT session_ref FROM identity.auth_session WHERE user_id=? AND realm_id=? ORDER BY session_ref FOR UPDATE";
    try(var s=c.prepareStatement(sql)) {
      s.setObject(1,initial.scope.equals("SESSION")?initial.session:initial.user);
      if(initial.scope.equals("ACCOUNT"))s.setObject(2,realm);
      try(var r=s.executeQuery()){while(r.next()){} }
    }
    return operation(c,id,true);
  }
  private Operation operation(Connection c,UUID id,boolean lock) throws SQLException {
    try(var s=c.prepareStatement("SELECT o.target_user_id,o.target_session_ref,s.context_id,o.credential_epoch,o.scope,o.action,encode(o.command_digest,'hex'),o.state,o.realm_id,o.topology_version,o.required_participants FROM identity.auth_operation o LEFT JOIN identity.auth_session s ON s.session_ref=o.target_session_ref WHERE o.operation_id=?"+(lock?" FOR UPDATE OF o":""))) {
      s.setObject(1,id);try(var r=s.executeQuery()) {
        if(!r.next())throw new Denied(404,"AUTH_OPERATION_NOT_FOUND");
        Object[] participants=(Object[])r.getArray(11).getArray();
        if(!realm.equals(r.getObject(9,UUID.class))||!AuthDeliveryWire.TOPOLOGY.equals(r.getString(10))
            ||!Arrays.equals(participants,new String[]{"content","learning"}))throw new Denied(409,"AUTH_OPERATION_BINDING_DENIED");
        return new Operation(id,r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),
            r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8));
      }
    }
  }
  private byte[] command(Operation op,String peer) {
    var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("operationId",op.id.toString());
    m.put("scope",op.scope);m.put("action",op.action);m.put("audience",peer);m.put("userId",op.user.toString());
    m.put("sessionRef",op.session==null?null:op.session.toString());m.put("sessionContextId",op.context==null?null:op.context.toString());
    m.put("epochFloor",Long.toString(op.floor));m.put("authRealmId",realm.toString());m.put("topologyVersion",AuthDeliveryWire.TOPOLOGY);
    m.put("participantGeneration",peers.get(peer).toString());m.put("commandDigest",op.digest);
    return WireCodec.JSON.writeValueAsBytes(m);
  }
  private void bound(AuthDeliveryWire.Command command,Operation op,String peer) {
    if(!Set.of("content","learning").contains(peer)||!peer.equals(command.consumer())||!op.id.equals(command.operation())
        ||!op.user.equals(command.user())||!Objects.equals(op.session,command.session())||!Objects.equals(op.context,command.context())
        ||op.floor!=command.floor()||!op.scope.equals(command.scope())||!op.action.equals(command.action())
        ||!op.digest.equals(command.digest())||!realm.equals(command.realm())||!peers.get(peer).equals(command.generation()))
      throw new Denied(409,"AUTH_OPERATION_BINDING_DENIED");
  }
  private void environment(Connection c) throws SQLException {
    Instant before=Instant.now();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT clock_timestamp(),current_database(),current_user,d.realm_id,d.db_generation,d.identity_generation,d.content_generation,d.learning_generation,d.ready,d.maintenance,a.realm_id,a.topology_version FROM identity.delivery_environment d CROSS JOIN identity.auth_environment a WHERE d.singleton AND a.singleton FOR SHARE OF d")) {
      if(!r.next()||!r.getString(2).equals("identity_db")||!r.getString(3).equals("identity_app")
          ||!realm.equals(r.getObject(4,UUID.class))||!generation.equals(r.getObject(5,UUID.class))
          ||!generation.equals(r.getObject(6,UUID.class))||!peers.get("content").equals(r.getObject(7,UUID.class))
          ||!peers.get("learning").equals(r.getObject(8,UUID.class))||!r.getBoolean(9)||r.getBoolean(10)
          ||!realm.equals(r.getObject(11,UUID.class))||!AuthDeliveryWire.TOPOLOGY.equals(r.getString(12)))
        throw new Denied(503,"AUTH_BARRIER_ENVIRONMENT_UNAVAILABLE");
      Instant time=r.getObject(1,OffsetDateTime.class).toInstant(),after=Instant.now();
      if(after.isBefore(before)||Duration.between(before,after).toMillis()>1000||time.isBefore(before.minusMillis(500))||time.isAfter(after.plusMillis(500)))
        throw new Denied(503,"CLOCK_UNAVAILABLE");
    }
  }
  private Connection transaction() throws SQLException {
    Connection c=source.getConnection();
    try {
      c.setAutoCommit(false);try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='2s'; SET LOCAL statement_timeout='5s'; SET LOCAL transaction_timeout='6s'");}
      return c;
    }catch(SQLException|RuntimeException failure){c.close();throw failure;}
  }
  private static String sha(String value) {
    try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("SHA256 unavailable");}
  }
}
