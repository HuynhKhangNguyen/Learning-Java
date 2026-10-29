package lab.backend.identity.session;

import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lab.backend.identity.credential.CredentialVerifier;
import lab.backend.identity.credential.PasswordCorpus;
import lab.backend.identity.transport.WireCodec.Denied;

/** Own Identity logout-all/password-change: proof consume và toàn bộ local deny
 * commit cùng transaction. KDF trước locks; network do worker sau commit. Không
 * xóa cookie, gia hạn idle, tạo RESET token hoặc giả lifecycle journal complete. */
public final class IdentitySensitiveAuthority {
  private final DataSource source;
  private final UUID realm;
  private final ReauthAuthority proofs;
  private final AuthBarrierCoordinator barrier;
  private final CredentialVerifier verifier;
  private final PasswordCorpus corpus;
  private final byte[] bindingKey;
  private record Grant(UUID ref,UUID user,UUID session,UUID context,long epoch,UUID realm,
      String purpose,UUID key,byte[] intent,Instant expiry,String state,UUID operation) {}
  private record Checked(String hash,UUID retry) {}

  public IdentitySensitiveAuthority(DataSource source,UUID realm,ReauthAuthority proofs,
      AuthBarrierCoordinator barrier,CredentialVerifier verifier,PasswordCorpus corpus,byte[] bindingKey) {
    this.source=Objects.requireNonNull(source);this.realm=Objects.requireNonNull(realm);
    this.proofs=Objects.requireNonNull(proofs);this.barrier=Objects.requireNonNull(barrier);
    this.verifier=Objects.requireNonNull(verifier);this.corpus=Objects.requireNonNull(corpus);
    IdentitySensitiveIntent.protectedDigest(bindingKey,"0".repeat(64));
    this.bindingKey=bindingKey.clone();
  }
  @Override public String toString(){return "IdentitySensitiveAuthority[ownIdentity,REDACTED]";}

  /** Startup chỉ đọc: thiếu deferred guard hoặc column privilege phải fail
   * closed; runtime không tự migrate/repair và không nhận key từ browser. */
  public void verifyRuntime()throws SQLException {
    proofs.verifyRuntime();
    try(Connection c=transaction()) {
      barrier.requireReady(c);clock(c);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE tgrelid='identity.auth_reauth_grant'::regclass AND tgname='auth_reauth_consumed_effect_guard' AND NOT tgisinternal AND tgenabled='O' AND tgdeferrable AND tginitdeferred")) {
        if(!r.next()||r.getInt(1)!=1)throw new Denied(503,"SENSITIVE_REAUTH_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT has_column_privilege(current_user,'identity.auth_reauth_grant','consumed_operation_ref','UPDATE') AND has_column_privilege(current_user,'identity.auth_reauth_grant','consumed_at','UPDATE') AND NOT has_table_privilege(current_user,'identity.auth_reauth_grant','UPDATE,DELETE,TRUNCATE')")) {
        if(!r.next()||!r.getBoolean(1))throw new Denied(503,"SENSITIVE_REAUTH_SCHEMA_UNAVAILABLE");
      }
      c.commit();
    }
  }

  /** Password chỉ dùng để mint proof qua singleton verifier; client SHA được
   * chuyển thành HMAC riêng trước lưu DB để không lộ password-derived digest. */
  public ReauthAuthority.IssuedProof issue(String cookie,UUID context,String csrf,String password,
      String trustedIp,UUID key,String purpose,String intentHash)throws SQLException {
    purpose(purpose);
    byte[] protectedIntent=protect(intentHash);
    return proofs.issueIdentitySensitive(cookie,context,csrf,password,trustedIp,key,purpose,protectedIntent);
  }

  /** Exact consumed retry chỉ đọc operation: không KDF/epoch bump lần hai, không
   * gia hạn proof/session và không tác động phiên B khi cookie/context A đổi.
   * TTL áp dụng lúc consume đầu; retry durable effect sau TTL không cấp quyền mới. */
  public UUID execute(String cookie,UUID context,String csrf,UUID key,String purpose,String proof,
      String newPassword)throws SQLException {
    purpose(purpose);
    if(context==null||key==null)throw new Denied(400,"BAD_REAUTH_BINDING");
    byte[] cookieDigest=handle(cookie),proofDigest=handle(proof);
    byte[] intent;
    try{intent=protect(IdentitySensitiveIntent.digest(purpose,context,key,newPassword));}
    catch(IllegalArgumentException invalid){throw new Denied(400,"BAD_REAUTH_BINDING");}
    Checked before;
    try(Connection c=transaction()) {
      before=check(c,cookieDigest,context,csrf,key,purpose,proofDigest,intent);
      c.commit();
    }
    if(before.retry!=null)return before.retry;
    String prepared=null;
    if(purpose.equals("PASSWORD_CHANGE")) {
      // Cả policy/corpus và expensive hash đều ngoài DB transaction. Không dùng
      // hasher/slots mới; locked-reread phía dưới loại bỏ stale KDF result.
      var result=verifier.preparePassword(newPassword,corpus);
      if(!result.result().accepted())throw new Denied(switch(result.result().code()){
        case "THROTTLED"->429;case "UNAVAILABLE"->503;default->400;},"NEW_PASSWORD_DENIED");
      prepared=result.hash();
    }
    try(Connection c=transaction()) {
      try {
        Checked current=check(c,cookieDigest,context,csrf,key,purpose,proofDigest,intent);
        if(current.retry!=null){c.commit();return current.retry;}
        if(!Objects.equals(current.hash,before.hash))throw new Denied(409,"REAUTH_PROOF_CONFLICT");
        Grant grant=grant(c,proofDigest,true);
        long floor;
        try{floor=Math.addExact(grant.epoch,1);}catch(ArithmeticException overflow){throw new Denied(409,"REAUTH_PROOF_CONFLICT");}
        UUID operation=UUID.randomUUID();
        byte[] command=java.util.HexFormat.of().parseHex(commandDigest(purpose,grant.user,floor));
        try(var s=c.prepareStatement("INSERT INTO identity.auth_operation(operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants) VALUES (?,?,?,?,?,?,?,?,'ACCOUNT',?,?,'r1-static-v1',ARRAY['content','learning'])")) {
          s.setObject(1,operation);s.setObject(2,key);s.setBytes(3,cookieDigest);s.setObject(4,context);
          s.setBytes(5,csrfDigest(csrf));s.setObject(6,grant.user);s.setLong(7,floor);s.setObject(8,realm);
          s.setString(9,purpose);s.setBytes(10,command);s.executeUpdate();
        }
        // Consume trước canonical deny để V7 kiểm current active binding; deferred
        // guard bắt buộc tất cả effect bên dưới có mặt trước COMMIT.
        try(var s=c.prepareStatement("UPDATE identity.auth_reauth_grant SET state='CONSUMED',consumed_operation_ref=?,consumed_at=clock_timestamp() WHERE proof_digest=? AND state='ISSUED'")) {
          s.setObject(1,operation);s.setBytes(2,proofDigest);
          if(s.executeUpdate()!=1)throw new Denied(409,"REAUTH_PROOF_CONFLICT");
        }
        try(var s=c.prepareStatement("UPDATE identity.app_user SET credential_epoch=?,auth_transition_pending=true,auth_pending_operation=?,password_hash=CASE WHEN ? THEN ? ELSE password_hash END,updated_at=clock_timestamp() WHERE user_id=? AND credential_epoch=?")) {
          s.setLong(1,floor);s.setObject(2,operation);s.setBoolean(3,purpose.equals("PASSWORD_CHANGE"));
          s.setString(4,prepared);s.setObject(5,grant.user);s.setLong(6,grant.epoch);
          if(s.executeUpdate()!=1)throw new Denied(409,"REAUTH_PROOF_CONFLICT");
        }
        try(var s=c.prepareStatement("UPDATE identity.credential_token SET invalidated_at=clock_timestamp() WHERE user_id=? AND consumed_at IS NULL AND invalidated_at IS NULL")) {
          s.setObject(1,grant.user);s.executeUpdate();
        }
        // All session rows đã lock theo UUID trước proof. Không sửa state terminal
        // cũ; backing rows của mọi old epoch đều bị xóa trong cùng transaction.
        try(var s=c.prepareStatement("UPDATE identity.auth_session SET state='REVOKING',csrf_token=NULL WHERE user_id=? AND realm_id=? AND credential_epoch<? AND state='ACTIVE'")) {
          s.setObject(1,grant.user);s.setObject(2,realm);s.setLong(3,floor);s.executeUpdate();
        }
        try(var s=c.prepareStatement("DELETE FROM identity.spring_session f USING identity.auth_session a WHERE f.session_id=a.framework_id AND a.user_id=? AND a.realm_id=? AND a.credential_epoch<?")) {
          s.setObject(1,grant.user);s.setObject(2,realm);s.setLong(3,floor);s.executeUpdate();
        }
        for(String participant:List.of("content","learning"))try(var s=c.prepareStatement("INSERT INTO identity.auth_fence_intent(operation_id,participant,command_digest) VALUES (?,?,?)")) {
          s.setObject(1,operation);s.setString(2,participant);s.setBytes(3,command);s.executeUpdate();
        }
        barrier.append(c,operation);
        try(var s=c.prepareStatement("INSERT INTO identity.security_audit(audit_id,event_type,target_user_id,result,correlation_id,detail_code) VALUES (?,'REAUTH_CONSUMED',?,'SUCCEEDED',?,'LOCAL_DENY_PENDING_FENCES')")) {
          s.setObject(1,UUID.randomUUID());s.setObject(2,grant.user);s.setObject(3,grant.ref);s.executeUpdate();
        }
        clock(c);c.commit();return operation;
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
  }

  /** Đọc owner từ immutable proof trước khi khóa account; không lock userId do
   * browser tự khai. Toàn bộ sessions khóa theo UUID rồi proof, tránh hai phiên
   * cùng logout-all giữ session khác nhau và deadlock. Không principal/idle effect. */
  private Checked check(Connection c,byte[] cookie,UUID context,String csrf,UUID key,String purpose,
      byte[] proof,byte[] intent)throws SQLException {
    Grant initial=grant(c,proof,false);bind(initial,context,key,purpose,intent);
    String hash;boolean active;
    try(var s=c.prepareStatement("SELECT password_hash,state='ACTIVE' AND NOT operator_disabled AND NOT auth_transition_pending AND credential_epoch=? FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
      s.setLong(1,initial.epoch);s.setObject(2,initial.user);try(var r=s.executeQuery()){
        if(!r.next())throw new Denied(401,"REAUTH_PROOF_DENIED");hash=r.getString(1);active=r.getBoolean(2);
      }
    }
    try(var s=c.prepareStatement("SELECT session_ref FROM identity.auth_session WHERE user_id=? ORDER BY session_ref FOR UPDATE")) {
      s.setObject(1,initial.user);try(var r=s.executeQuery()){while(r.next()){/* Giữ tất cả row locks tới transaction kết thúc. */}}
    }
    Grant current=grant(c,proof,true);bind(current,context,key,purpose,intent);Instant time=clock(c);
    if(current.state.equals("CONSUMED")) {
      try(var s=c.prepareStatement("SELECT operation_id FROM identity.auth_operation WHERE operation_id=? AND target_user_id=? AND initiating_cookie_digest=? AND initiating_context_id=? AND initiating_csrf_digest=? AND idempotency_key=? AND realm_id=? AND action=? AND scope='ACCOUNT' AND initiator_kind='BROWSER'")) {
        s.setObject(1,current.operation);s.setObject(2,current.user);s.setBytes(3,cookie);s.setObject(4,context);
        s.setBytes(5,csrfDigest(csrf));s.setObject(6,key);s.setObject(7,realm);s.setString(8,purpose);
        try(var r=s.executeQuery()){if(!r.next())throw new Denied(409,"REAUTH_PROOF_CONFLICT");return new Checked(hash,r.getObject(1,UUID.class));}
      }
    }
    if(!current.state.equals("ISSUED")||!active||!time.isBefore(current.expiry))throw new Denied(401,"REAUTH_PROOF_DENIED");
    barrier.requireReady(c);
    try(var s=c.prepareStatement("SELECT csrf_token FROM identity.auth_session s WHERE session_ref=? AND user_id=? AND cookie_digest=? AND context_id=? AND credential_epoch=? AND realm_id=? AND state='ACTIVE' AND issued_at+interval '12 hours'>clock_timestamp() AND last_interactive_at+interval '30 minutes'>clock_timestamp() AND EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=s.framework_id AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000)")) {
      s.setObject(1,current.session);s.setObject(2,current.user);s.setBytes(3,cookie);s.setObject(4,context);s.setLong(5,current.epoch);s.setObject(6,realm);
      try(var r=s.executeQuery()){
        if(!r.next())throw new Denied(401,"REAUTH_PROOF_DENIED");
        if(!MessageDigest.isEqual(csrfDigest(r.getString(1)),csrfDigest(csrf)))throw new Denied(403,"CSRF_DENIED");
      }
    }
    return new Checked(hash,null);
  }
  private Grant grant(Connection c,byte[] digest,boolean lock)throws SQLException {
    try(var s=c.prepareStatement("SELECT grant_ref,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,expires_at,state,consumed_operation_ref FROM identity.auth_reauth_grant WHERE proof_digest=?"+(lock?" FOR UPDATE":""))) {
      s.setBytes(1,digest);try(var r=s.executeQuery()){
        if(!r.next())throw new Denied(401,"REAUTH_PROOF_DENIED");
        return new Grant(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getLong(5),r.getObject(6,UUID.class),r.getString(7),r.getObject(8,UUID.class),r.getBytes(9),r.getObject(10,OffsetDateTime.class).toInstant(),r.getString(11),r.getObject(12,UUID.class));
      }
    }
  }
  private void bind(Grant grant,UUID context,UUID key,String purpose,byte[] intent) {
    if(!realm.equals(grant.realm)||!context.equals(grant.context)||!key.equals(grant.key)
        ||!purpose.equals(grant.purpose)||!MessageDigest.isEqual(intent,grant.intent))throw new Denied(409,"REAUTH_PROOF_CONFLICT");
  }
  private byte[] protect(String intent) {
    try{return IdentitySensitiveIntent.protectedDigest(bindingKey,intent);}
    catch(IllegalArgumentException invalid){throw new Denied(400,"BAD_REAUTH_BINDING");}
  }
  private static void purpose(String value){if(value==null||!Set.of("LOGOUT_ALL","PASSWORD_CHANGE").contains(value))throw new Denied(400,"BAD_REAUTH_BINDING");}
  private static byte[] handle(String value) {
    if(value==null||!value.matches("[A-Za-z0-9_-]{43}"))throw new Denied(401,"REAUTH_PROOF_DENIED");
    return SessionAuthority.digest(value);
  }
  private static byte[] csrfDigest(String value) {
    byte[] result=SessionAuthority.digest(value);
    if(result==null)throw new Denied(403,"CSRF_DENIED");
    return result;
  }
  private String commandDigest(String purpose,UUID user,long floor) {
    // SessionAuthority.digest chỉ dành cho opaque handle43; command là bytes
    // canon dài tùy UUID/epoch, phải SHA-256 riêng như account RESET contract.
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
      (purpose+"\n"+user+"\nACCOUNT\n"+floor+"\n"+realm+"\nr1-static-v1\ncontent,learning").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable");}
  }
  private Connection transaction()throws SQLException {
    Connection c=source.getConnection();try{
      c.setAutoCommit(false);try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='2s'; SET LOCAL statement_timeout='5s'; SET LOCAL transaction_timeout='6s'");}return c;
    }catch(SQLException|RuntimeException failure){c.close();throw failure;}
  }
  private Instant clock(Connection c)throws SQLException {
    Instant before=Instant.now();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT clock_timestamp(),current_database(),current_user,realm_id,topology_version FROM identity.auth_environment WHERE singleton")) {
      if(!r.next()||!r.getString(2).equals("identity_db")||!r.getString(3).equals("identity_app")||!realm.equals(r.getObject(4,UUID.class))||!r.getString(5).equals("r1-static-v1"))throw new Denied(503,"AUTH_ENVIRONMENT_UNAVAILABLE");
      Instant time=r.getObject(1,OffsetDateTime.class).toInstant(),after=Instant.now();
      if(after.isBefore(before)||Duration.between(before,after).toMillis()>1000||time.isBefore(before.minusMillis(500))||time.isAfter(after.plusMillis(500)))throw new Denied(503,"CLOCK_UNAVAILABLE");
      return time;
    }
  }
}
