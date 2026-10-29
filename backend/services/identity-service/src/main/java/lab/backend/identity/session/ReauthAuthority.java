package lab.backend.identity.session;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lab.backend.identity.transport.WireCodec.Denied;

/**
 * Own Identity proof/reservation cho reset học, không phải learner session/token
 * exchange hoặc reset commit. Password chỉ qua canonical reauth callback; raw
 * proof trả đúng lần mint, DB chỉ giữ digest. Không refund/reopen proof khi retry.
 * Logout-all/password-change dùng IdentitySensitiveAuthority để bind payload;
 * INT06 vẫn chỉ dành cho LEARNING_RESET, không consume Identity proof.
 */
public final class ReauthAuthority {
  public static final String PURPOSE="LEARNING_RESET";
  private final DataSource source;
  private final UUID realm;
  private final SessionAuthority sessions;
  private final AuthBarrierCoordinator barrier;
  private final SecureRandom random=new SecureRandom();
  public record IssuedProof(String proof,UUID grantRef,Instant expiresAt) {
    @Override public String toString(){return "IssuedReauthProof[REDACTED]";}
  }
  /** Caller Learning phải bind cùng request/admission hiện hành; record không
   * chứa password. Wire adapter sau phải parse exact schema, không browser caller. */
  public record Request(String proof,UUID user,UUID session,UUID context,long epoch,UUID realm,
      String purpose,UUID operationKey,String intentHash) {
    @Override public String toString(){return "ReauthReservationRequest[REDACTED]";}
  }
  public record Reservation(UUID reference,UUID grantRef,UUID user,UUID session,UUID context,long epoch,
      UUID realm,String purpose,UUID operationKey,String intentHash,Instant expiresAt) {
    @Override public String toString(){return "ReauthReservation[bound,REDACTED]";}
  }
  private record Grant(UUID ref,UUID user,UUID session,UUID context,long epoch,UUID realm,String purpose,
      UUID key,byte[] intent,Instant expiry,String state,UUID reservation) {}

  public ReauthAuthority(DataSource source,UUID realm,SessionAuthority sessions,AuthBarrierCoordinator barrier) {
    this.source=Objects.requireNonNull(source);this.realm=Objects.requireNonNull(realm);
    this.sessions=Objects.requireNonNull(sessions);this.barrier=Objects.requireNonNull(barrier);
  }
  @Override public String toString(){return "ReauthAuthority[ownIdentity,REDACTED]";}

  /** Startup chỉ verify own ready environment/role/schema/privileges, không tạo
   * grant, migrate/provision hoặc repair trigger/capacity thiếu. */
  public void verifyRuntime()throws SQLException {
    try(Connection c=transaction()) {
      barrier.requireReady(c);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O' AND (tgrelid,tgname) IN (('identity.auth_reauth_grant'::regclass,'auth_reauth_insert_guard'),('identity.auth_reauth_grant'::regclass,'auth_reauth_transition_guard'))")) {
        if(!r.next()||r.getInt(1)!=2)throw new Denied(503,"REAUTH_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT max_grants,has_table_privilege(current_user,'identity.auth_reauth_capacity','INSERT,UPDATE,DELETE,TRUNCATE'),has_table_privilege(current_user,'identity.auth_reauth_grant','UPDATE,DELETE,TRUNCATE'),has_column_privilege(current_user,'identity.auth_reauth_grant','state','UPDATE') AND has_column_privilege(current_user,'identity.auth_reauth_grant','reservation_ref','UPDATE') AND has_column_privilege(current_user,'identity.auth_reauth_grant','reserved_at','UPDATE') FROM identity.auth_reauth_capacity WHERE singleton")) {
        if(!r.next()||r.getInt(1)!=100000||r.getBoolean(2)||r.getBoolean(3)||!r.getBoolean(4))throw new Denied(503,"REAUTH_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement()){s.executeQuery("SELECT proof_digest,grant_ref,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,issued_at,expires_at,state,reservation_ref,reserved_at FROM identity.auth_reauth_grant LIMIT 0").close();}
      c.commit();
    }
  }

  /** Mint proof sau password/KDF và locked canonical reread. Intent hash là
   * opaque binding của future reset owner, không tự diễn giải learner payload. */
  public IssuedProof issueLearningReset(String cookie,UUID context,String csrf,String password,String trustedIp,
      UUID operationKey,String intentHash)throws SQLException {
    return issue(cookie,context,csrf,password,trustedIp,operationKey,PURPOSE,intent(intentHash));
  }

  /** Chỉ own Identity authority gọi sau khi chuyển client intent thành keyed
   * digest. Không expose generic purpose ở INT06 hoặc lưu password-derived SHA. */
  IssuedProof issueIdentitySensitive(String cookie,UUID context,String csrf,String password,String trustedIp,
      UUID operationKey,String purpose,byte[] protectedIntent)throws SQLException {
    if(!Set.of("LOGOUT_ALL","PASSWORD_CHANGE").contains(purpose)||protectedIntent==null||protectedIntent.length!=32)
      throw new Denied(400,"BAD_REAUTH_BINDING");
    return issue(cookie,context,csrf,password,trustedIp,operationKey,purpose,protectedIntent);
  }

  private IssuedProof issue(String cookie,UUID context,String csrf,String password,String trustedIp,
      UUID operationKey,String purpose,byte[] intent)throws SQLException {
    if(operationKey==null)throw new Denied(400,"BAD_REAUTH_BINDING");
    return sessions.reauthenticate(cookie,context,csrf,password,trustedIp,(c,current)->{
      barrier.requireReady(c);if(!realm.equals(current.realm()))throw new Denied(409,"REAUTH_REALM_DENIED");
      // Account → session → environment → capacity. Quota trước INSERT để trả
      // 429 rõ; SQL trigger vẫn là backstop chống concurrent quota overshoot.
      try(var s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(7404,2)");}
      try(var s=c.prepareStatement("SELECT (SELECT count(*) FROM identity.auth_reauth_grant),max_grants,(SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id=? AND expires_at>clock_timestamp()) FROM identity.auth_reauth_capacity WHERE singleton")) {
        s.setObject(1,current.user());try(var r=s.executeQuery()) {
          if(!r.next())throw new Denied(503,"REAUTH_SCHEMA_UNAVAILABLE");
          if(r.getLong(1)>=r.getInt(2)||r.getLong(3)>=20)throw new Denied(429,"REAUTH_CAPACITY");
        }
      }
      byte[] entropy=new byte[32];random.nextBytes(entropy);String proof=Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
      UUID ref=UUID.randomUUID();Instant expiry=current.time().plusSeconds(300);
      try(var s=c.prepareStatement("INSERT INTO identity.auth_reauth_grant(proof_digest,grant_ref,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,issued_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
        s.setBytes(1,SessionAuthority.digest(proof));s.setObject(2,ref);s.setObject(3,current.user());s.setObject(4,current.session());
        s.setObject(5,current.context());s.setLong(6,current.epoch());s.setObject(7,realm);s.setString(8,purpose);s.setObject(9,operationKey);s.setBytes(10,intent);
        s.setObject(11,utc(current.time()));s.setObject(12,utc(expiry));s.executeUpdate();
      }
      audit(c,current.user(),ref,"REAUTH_ISSUED");return new IssuedProof(proof,ref,expiry);
    });
  }

  /**
   * Chỉ verified Learning caller được reserve. Khóa account → session → grant;
   * không KDF/network/idle touch. Same intent đọc lại exact reservation/TTL, không
   * UPDATE reserved row; crash/lost response không refund sang operation khác.
   */
  public Reservation reserve(Request request,String verifiedCaller)throws SQLException {
    if(!"learning".equals(verifiedCaller))throw new Denied(403,"REAUTH_CALLER_DENIED");
    if(request==null||request.user==null||request.session==null||request.context==null||request.realm==null
        ||request.operationKey==null||request.epoch<1||!PURPOSE.equals(request.purpose))throw new Denied(400,"BAD_REAUTH_BINDING");
    byte[] key=SessionAuthority.digest(request.proof);if(key==null)throw new Denied(401,"REAUTH_PROOF_DENIED");
    byte[] expectedIntent=intent(request.intentHash);
    try(Connection c=transaction()) {
      try {
        Grant initial=grant(c,key,false);bound(initial,request,expectedIntent);
        // Owner/session lấy từ stored immutable proof, không lock user do client
        // tự khai. Account pending/epoch đổi trong lúc chờ được đọc lại dưới lock.
        try(var s=c.prepareStatement("SELECT state='ACTIVE' AND NOT operator_disabled AND NOT auth_transition_pending AND credential_epoch=? FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
          s.setLong(1,initial.epoch);s.setObject(2,initial.user);try(var r=s.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new Denied(401,"REAUTH_PROOF_DENIED");}
        }
        try(var s=c.prepareStatement("SELECT state='ACTIVE' AND realm_id=? AND issued_at+interval '12 hours'>clock_timestamp() AND last_interactive_at+interval '30 minutes'>clock_timestamp() AND EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=framework_id AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000) FROM identity.auth_session WHERE session_ref=? FOR UPDATE")) {
          s.setObject(1,realm);s.setObject(2,initial.session);try(var r=s.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new Denied(401,"REAUTH_PROOF_DENIED");}
        }
        barrier.requireReady(c);Grant current=grant(c,key,true);bound(current,request,expectedIntent);
        // Clock timestamp ở own DB sau khi chờ locks, không client time hoặc TTL
        // renewal. Expired reserved proof không cấp reference mới cho late retry.
        Instant time;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT clock_timestamp()")){r.next();time=r.getObject(1,OffsetDateTime.class).toInstant();}
        if(!time.isBefore(current.expiry))throw new Denied(401,"REAUTH_PROOF_DENIED");
        UUID reservation=current.reservation;
        if(current.state.equals("ISSUED")) {
          reservation=UUID.randomUUID();
          try(var s=c.prepareStatement("UPDATE identity.auth_reauth_grant SET state='RESERVED',reservation_ref=?,reserved_at=? WHERE proof_digest=? AND state='ISSUED'")) {
            s.setObject(1,reservation);s.setObject(2,utc(time));s.setBytes(3,key);if(s.executeUpdate()!=1)throw new Denied(409,"REAUTH_PROOF_CONFLICT");
          }
          audit(c,current.user,current.ref,"REAUTH_RESERVED");
        }
        var result=new Reservation(reservation,current.ref,current.user,current.session,current.context,current.epoch,realm,PURPOSE,current.key,
            HexFormat.of().formatHex(current.intent),current.expiry);
        c.commit();return result;
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
  }
  private Grant grant(Connection c,byte[] key,boolean lock)throws SQLException {
    try(var s=c.prepareStatement("SELECT grant_ref,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,expires_at,state,reservation_ref FROM identity.auth_reauth_grant WHERE proof_digest=?"+(lock?" FOR UPDATE":""))) {
      s.setBytes(1,key);try(var r=s.executeQuery()) {
        if(!r.next())throw new Denied(401,"REAUTH_PROOF_DENIED");
        return new Grant(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getLong(5),r.getObject(6,UUID.class),r.getString(7),r.getObject(8,UUID.class),r.getBytes(9),r.getObject(10,OffsetDateTime.class).toInstant(),r.getString(11),r.getObject(12,UUID.class));
      }
    }
  }
  private void bound(Grant grant,Request request,byte[] intent) {
    if(!realm.equals(request.realm)||!realm.equals(grant.realm)||!grant.user.equals(request.user)||!grant.session.equals(request.session)
        ||!grant.context.equals(request.context)||grant.epoch!=request.epoch||!grant.purpose.equals(request.purpose)
        ||!grant.key.equals(request.operationKey)||!MessageDigest.isEqual(grant.intent,intent))throw new Denied(409,"REAUTH_PROOF_CONFLICT");
  }
  private static byte[] intent(String value) {
    if(value==null||!value.matches("[0-9a-f]{64}"))throw new Denied(400,"BAD_REAUTH_BINDING");
    return HexFormat.of().parseHex(value);
  }
  private Connection transaction()throws SQLException {
    Connection c=source.getConnection();try {
      c.setAutoCommit(false);try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='2s'; SET LOCAL statement_timeout='5s'; SET LOCAL transaction_timeout='6s'");}return c;
    }catch(SQLException|RuntimeException failure){c.close();throw failure;}
  }
  private static OffsetDateTime utc(Instant time){return time.atOffset(ZoneOffset.UTC);}
  private static void audit(Connection c,UUID user,UUID ref,String event)throws SQLException {
    try(var s=c.prepareStatement("INSERT INTO identity.security_audit(audit_id,event_type,target_user_id,result,correlation_id,detail_code) VALUES (?,?,?,'SUCCEEDED',?,'BOUND_PROOF')")) {
      s.setObject(1,UUID.randomUUID());s.setString(2,event);s.setObject(3,user);s.setObject(4,ref);s.executeUpdate();
    }
  }
}
