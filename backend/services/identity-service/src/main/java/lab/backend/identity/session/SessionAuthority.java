package lab.backend.identity.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lab.backend.identity.credential.AuthThrottle;
import lab.backend.identity.credential.CredentialVerifier;
import lab.backend.identity.credential.CredentialService;
import lab.backend.identity.credential.CredentialWorkflow.Purpose;
import lab.backend.identity.transport.WireCodec.Denied;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.support.TransactionOperations;

/** Authority canonical của Identity; row framework không tự cấp quyền request.
 * Mọi effect khóa account trước session/preauth; không KDF hoặc network dưới lock. */
public final class SessionAuthority {
  public static final String COOKIE="__Host-backend-lab-session";
  public static final Duration IDLE=Duration.ofMinutes(30), ABSOLUTE=Duration.ofHours(12);
  public record View(String state,UUID contextId,String csrfToken,UUID userId,String credentialEpoch,UUID operationId) {
    @Override public String toString(){return "SessionView[state="+state+",secrets=REDACTED]";}
  }
  public record Issued(String cookie,View view,int maxAge,Instant expiresAt) {
    public Issued(String cookie,View view,int maxAge){this(cookie,view,maxAge,Instant.now().plusSeconds(maxAge));}
    @Override public String toString(){return "IssuedSession[secrets=REDACTED]";}
  }
  public record Consumed(String state,UUID operationId) {}
  /** Snapshot chỉ cấp dưới current account/session locks, không nhận từ browser.
   * Callback dùng cùng transaction, không commit/KDF/network hoặc đổi ownership. */
  public record VerifiedSession(UUID user,UUID session,UUID context,long epoch,UUID realm,Instant time) {
    @Override public String toString(){return "VerifiedSession[canonical,REDACTED]";}
  }
  @FunctionalInterface public interface ReauthenticatedAction<T> {T apply(Connection transaction,VerifiedSession current)throws SQLException;}
  /** Admission chỉ sống trong request mTLS; không phải bearer token/cache ALLOW.
   * Không chứa cookie hay CSRF. UUID/owner cũng không được đưa vào log tự động. */
  public record Admission(UUID id,UUID nonce,String caller,UUID realm,UUID user,UUID session,
      UUID context,long epoch,String action,String binding,Instant issued,Instant until,
      Instant idle,Instant absolute,boolean csrfVerified) {
    public Map<String,Object> json() {
      var value=new LinkedHashMap<String,Object>();
      value.put("schemaVersion",1);value.put("decision","ALLOW");value.put("admissionId",id.toString());
      value.put("requestNonce",nonce.toString());value.put("provider","identity");
      value.put("caller",caller);value.put("audience",caller);value.put("authRealmId",realm.toString());
      value.put("userId",user.toString());value.put("sessionRef",session.toString());
      value.put("sessionContextId",context.toString());value.put("credentialEpoch",Long.toString(epoch));
      value.put("action",action);value.put("requestBinding",binding);
      // Wire yêu cầu đúng ba chữ số millisecond; floor deadline không kéo dài quyền.
      var format=new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
      value.put("issuedAt",format.format(issued));value.put("serverTime",format.format(issued));
      value.put("validUntil",format.format(until));value.put("idleDeadline",format.format(idle));
      value.put("absoluteDeadline",format.format(absolute));value.put("csrfVerified",csrfVerified);
      return Map.copyOf(value);
    }
    @Override public String toString(){return "CanonicalAdmission[requestScoped,authority=REDACTED]";}
  }
  private record Guard(UUID ref,UUID context,UUID user,long epoch,UUID realm,String state,
                       String csrf,Instant issued,Instant interactive,String framework) {}
  private final DataSource ds;
  private final UUID realm;
  private final CredentialVerifier verifier;
  private final AuthThrottle throttle;
  private final CredentialService credentials;
  private final AuthBarrierCoordinator barrier;
  private final SecureRandom random=new SecureRandom();

  public SessionAuthority(DataSource ds,UUID realm,CredentialVerifier verifier,AuthThrottle throttle) {
    this(ds,realm,verifier,throttle,null);
  }
  public SessionAuthority(DataSource ds,UUID realm,CredentialVerifier verifier,AuthThrottle throttle,CredentialService credentials) {
    this(ds,realm,verifier,throttle,credentials,null);
  }
  /** Composition R04d explicit; constructor cũ giữ standalone local-only R04a.
   * Runtime canonical mới phải cấp barrier đã verify, không fallback khi config lỗi. */
  public SessionAuthority(DataSource ds,UUID realm,CredentialVerifier verifier,AuthThrottle throttle,
      CredentialService credentials,AuthBarrierCoordinator barrier) {
    this.ds=ds;this.realm=realm;this.verifier=verifier;this.throttle=throttle;this.credentials=credentials;this.barrier=barrier;
  }

  /** Read-only startup qualification, never creates or repairs runtime schema. */
  public void verifyRuntime() throws SQLException {
    try(Connection c=transaction()) {
      now(c);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR pg_has_role(current_user,'identity_migrator','MEMBER') OR pg_has_role(current_user,'identity_owner','MEMBER') FROM pg_roles WHERE rolname=current_user")) {
        if(!r.next() || r.getBoolean(1))throw new Denied(503,"AUTH_SCHEMA_UNAVAILABLE");
      }
      for(String table:new String[]{"auth_environment","auth_preauth_capacity","auth_preauth","auth_session","auth_operation","auth_fence_intent","spring_session","spring_session_attributes","app_user","credential_token","auth_throttle","security_audit"}) {
        try(var s=c.prepareStatement("SELECT has_table_privilege(current_user,?,'SELECT'),has_table_privilege(current_user,?,'TRUNCATE'),has_schema_privilege(current_user,'identity','CREATE')")) {
          s.setString(1,"identity."+table);s.setString(2,"identity."+table);
          try(var r=s.executeQuery()){if(!r.next() || !r.getBoolean(1) || r.getBoolean(2) || r.getBoolean(3))throw new Denied(503,"AUTH_SCHEMA_UNAVAILABLE");}
        }
      }
      for(String table:new String[]{"auth_environment","auth_preauth_capacity","auth_preauth","auth_session","auth_operation","auth_fence_intent"}) {
        String forbidden=SetPrivileges.forTable(table);
        try(var s=c.prepareStatement("SELECT has_table_privilege(current_user,?,?)")) {
          s.setString(1,"identity."+table);s.setString(2,forbidden);
          try(var r=s.executeQuery()){if(!r.next() || r.getBoolean(1))throw new Denied(503,"AUTH_SCHEMA_UNAVAILABLE");}
        }
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgenabled='O' AND (tgrelid,tgname) IN (( 'identity.auth_session'::regclass,'session_transition_guard'),('identity.auth_preauth'::regclass,'preauth_transition_guard'),('identity.auth_preauth'::regclass,'preauth_capacity_guard'))")) {
        if(!r.next() || r.getInt(1)!=3)throw new Denied(503,"AUTH_SCHEMA_UNAVAILABLE");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT max_flows FROM identity.auth_preauth_capacity WHERE singleton")) {
        if(!r.next() || r.getInt(1)!=100000)throw new Denied(503,"AUTH_SCHEMA_UNAVAILABLE");
      }
      // Compile the actual columns used by the authority/backing store. Missing
      // columns fail startup without granting the app access to migration secrets.
      for(String sql:new String[]{"SELECT cookie_digest,context_id,realm_id,csrf_token,expires_at,consumed_at FROM identity.auth_preauth LIMIT 0",
          "SELECT session_ref,cookie_digest,framework_id,context_id,user_id,credential_epoch,realm_id,state,csrf_token,issued_at,last_interactive_at FROM identity.auth_session LIMIT 0",
          "SELECT operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,realm_id,state,required_participants,topology_version FROM identity.auth_operation LIMIT 0",
          "SELECT primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name FROM identity.spring_session LIMIT 0",
          "SELECT session_primary_id,attribute_name,attribute_bytes FROM identity.spring_session_attributes LIMIT 0"}) {
        try(var s=c.createStatement()){s.executeQuery(sql).close();}
      }
      c.commit();
    }
  }
  private static final class SetPrivileges {
    static String forTable(String table){return switch(table){case "auth_environment","auth_preauth_capacity"->"INSERT,UPDATE,DELETE";case "auth_operation","auth_fence_intent"->"UPDATE,DELETE";default->"DELETE";};}
  }

  /** Bounded background expiry; the access path remains authoritative if this
   * worker is late/down. Retains tombstones and PENDING_FENCES across restarts. */
  public int sweepExpired(int limit) throws SQLException {
    if(limit<1 || limit>100)throw new IllegalArgumentException("Bounded sweep requires 1..100");
    var keys=new java.util.ArrayList<byte[]>();
    try(Connection c=transaction()) {
      now(c);
      try(var s=c.prepareStatement("SELECT a.cookie_digest FROM identity.auth_session a JOIN identity.app_user u ON u.user_id=a.user_id WHERE a.realm_id=? AND a.state='ACTIVE' AND (a.issued_at+interval '12 hours'<=clock_timestamp() OR a.last_interactive_at+interval '30 minutes'<=clock_timestamp() OR u.state<>'ACTIVE' OR u.operator_disabled OR u.auth_transition_pending OR u.credential_epoch<>a.credential_epoch OR NOT EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=a.framework_id AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000)) ORDER BY a.session_ref LIMIT ?")) {
        s.setObject(1,realm);s.setInt(2,limit);try(var r=s.executeQuery()){while(r.next())keys.add(r.getBytes(1));}
      }
      c.commit();
    }
    int changed=0;
    for(byte[] key:keys)try(Connection c=transaction()) {
      try {
        now(c);Guard initial=lookup(c,key,false);
        if(initial==null || !initial.state.equals("ACTIVE")){c.commit();continue;}
        boolean account=lockAccount(c,initial.user,initial.epoch);
        Guard current=lookup(c,key,true),result=enforce(c,key,current,account);
        if(current.state.equals("ACTIVE") && !result.state.equals("ACTIVE"))changed++;
        c.commit();
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
    return changed;
  }

  /** Chỉ explicit credential bootstrap mới cấp preauth/cookie mới; GET/status không
   * rotate. Cookie canonical đã terminal chỉ được đổi flow sau barrier COMPLETED. */
  public Issued begin(String previousCookie) throws SQLException {
    try(Connection c=transaction()) {
      try {
        Instant time=now(c);
        byte[] previousKey=digest(previousCookie);
        Guard initial=previousKey==null?null:lookup(c,previousKey,false);
        if(initial!=null) {
          // Cùng thứ tự account → session với logout/completion. Không lấy lock
          // preauth trước account: login đang giữ account rồi consume preauth.
          lockAccount(c,initial.user,initial.epoch);
          Guard current=lookup(c,previousKey,true);
          if(!realm.equals(current.realm)||current.state.equals("ACTIVE"))
            throw new Denied(409,"SESSION_SWITCH_REQUIRES_LOGOUT");
          if(!current.state.equals("REVOKED")||pendingOperation(c,previousKey)!=null
              ||!completedFence(c,current))throw new Denied(409,"AUTH_TRANSITION_PENDING");
          // Không sửa/hồi sinh session cũ hoặc đổi account gate. Preauth mới
          // phía dưới chỉ cho bước credential; login vẫn kiểm epoch/pending riêng.
        } else if(previousKey!=null) {
          // Flow anonymous giữ lock preauth để serialize consume một lần; nhánh
          // này không lấy account/session lock, nên không đảo thứ tự khóa login.
          try(var s=c.prepareStatement("SELECT consumed_at IS NULL AND expires_at>clock_timestamp(),context_id,csrf_token FROM identity.auth_preauth WHERE cookie_digest=? AND realm_id=? FOR UPDATE")) {
            s.setBytes(1,previousKey);s.setObject(2,realm);
            try(var r=s.executeQuery()) {
              if(r.next()) {
                if(r.getBoolean(1)){var view=new View("ANONYMOUS",r.getObject(2,UUID.class),r.getString(3),null,null,null);c.commit();return new Issued(null,view,0);}
                // Flow consumed giữ tombstone, không mở lại. Reset còn pending
                // phải giữ initiating binding để status, không rotate cookie.
                if(pendingOperation(c,previousKey)!=null)throw new Denied(409,"AUTH_TRANSITION_PENDING");
              }
            }
          }
        }
        String cookie=secret(),csrf=secret();UUID context=UUID.randomUUID();
        // Capacity lock serialize quota anonymous, không tạo flow vô hạn. Không
        // xóa tombstone hết hạn ở runtime; retention thuộc operator gate riêng.
        try(var s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(7404,1)");}
        try(var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT count(*) FROM identity.auth_preauth),max_flows FROM identity.auth_preauth_capacity WHERE singleton")) {
          if(!r.next())throw new Denied(503,"PREAUTH_CAPACITY_UNAVAILABLE");
          if(r.getLong(1)>=r.getInt(2))throw new Denied(429,"PREAUTH_CAPACITY");
        }
        try(var s=c.prepareStatement("INSERT INTO identity.auth_preauth(cookie_digest,context_id,realm_id,csrf_token,created_at,expires_at) VALUES (?,?,?,?,?,?)")) {
          s.setBytes(1,digest(cookie));s.setObject(2,context);s.setObject(3,realm);s.setString(4,csrf);
          s.setObject(5,utc(time));s.setObject(6,utc(time.plusSeconds(900)));s.executeUpdate();
        }
        c.commit();return new Issued(cookie,new View("ANONYMOUS",context,csrf,null,null,null),900,time.plusSeconds(900));
      }catch(SQLException|RuntimeException e){c.rollback();throw e;}
    }
  }

  public Issued login(String cookie,UUID context,String csrf,String login,String password,String trustedIp) throws SQLException {
    requirePreauth(cookie,context,csrf); // reject CSRF before throttle/KDF
    AtomicReference<Issued> issued=new AtomicReference<>();
    AtomicReference<Denied> bindingFailure=new AtomicReference<>();
    var result=verifier.verifyAndApply(ds,throttle,login,password,trustedIp,(c,user,epoch)->{
      try {
        Instant time=now(c);
        consumePreauth(c,cookie,context,csrf);
        String raw=secret(),newCsrf=secret(),framework=HexFormat.of().formatHex(digest(raw));
        UUID ref=UUID.randomUUID(),newContext=UUID.randomUUID();
        try(var s=c.prepareStatement("INSERT INTO identity.auth_session(session_ref,cookie_digest,framework_id,context_id,user_id,credential_epoch,realm_id,state,csrf_token,issued_at,last_interactive_at) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?,?)")) {
          s.setObject(1,ref);s.setBytes(2,digest(raw));s.setString(3,framework);s.setObject(4,newContext);s.setObject(5,user);s.setLong(6,epoch);s.setObject(7,realm);s.setString(8,newCsrf);s.setObject(9,utc(time));s.setObject(10,utc(time));s.executeUpdate();
        }
        var repository=repository(c,framework);
        Session backing=repository.createSession();backing.setLastAccessedTime(time);backing.setMaxInactiveInterval(ABSOLUTE);
        repository.save(backing); // joins this manually-owned SQL transaction; no separate commit
        issued.set(new Issued(raw,new View("ACTIVE",newContext,newCsrf,user,Long.toString(epoch),null),(int)ABSOLUTE.toSeconds(),time.plus(ABSOLUTE)));
      }catch(Denied denied){bindingFailure.set(denied);throw denied;}
    });
    if(bindingFailure.get()!=null)throw bindingFailure.get();
    if(!result.accepted())throw new Denied(switch(result.code()){case "THROTTLED"->429;case "UNAVAILABLE"->503;default->401;},result.code());
    return issued.get();
  }

  public View bootstrap(String cookie) throws SQLException {
    byte[] key=digest(cookie);
    try(Connection c=transaction()) {
      try {
        now(c);
        if(key==null){c.commit();return anonymous();}
        Guard initial=lookup(c,key,false);
        if(initial==null) {
          UUID pending=pendingOperation(c,key);
          if(pending!=null){c.commit();return new View("REVOKING",operationContext(c,pending),null,null,null,pending);}
          try(var s=c.prepareStatement("SELECT context_id,csrf_token FROM identity.auth_preauth WHERE cookie_digest=? AND realm_id=? AND consumed_at IS NULL AND expires_at>clock_timestamp()")) {
            s.setBytes(1,key);s.setObject(2,realm);try(var r=s.executeQuery()) {
              View view=r.next()?new View("ANONYMOUS",r.getObject(1,UUID.class),r.getString(2),null,null,null):anonymous();c.commit();return view;
            }
          }
        }
        boolean account=lockAccount(c,initial.user,initial.epoch);
        Guard guard=lookup(c,key,true);
        guard=enforce(c,key,guard,account);
        View view=guard.state.equals("ACTIVE")?new View("ACTIVE",guard.context,guard.csrf,guard.user,Long.toString(guard.epoch),null)
            :new View(guard.state,guard.context,null,null,null,pendingOperation(c,key));
        c.commit();return view;
      }catch(SQLException|RuntimeException e){c.rollback();throw e;}
    }
  }

  /** No auto-login or cookie rotation. Token, password, preauth, account deny
   * and reset intents commit together. Lost responses recover via bootstrap/status,
   * not a second password mutation with a consumed flow. */
  public Consumed consume(Purpose purpose,String cookie,UUID context,String csrf,String token,String password,
      String trustedIp,UUID idempotencyKey) throws SQLException {
    if(purpose==Purpose.RESET && idempotencyKey==null)throw new Denied(400,"IDEMPOTENCY_REQUIRED");
    requirePreauth(cookie,context,csrf);
    if(credentials==null)throw new Denied(503,"CREDENTIAL_FLOW_UNAVAILABLE");
    AtomicReference<Consumed> outcome=new AtomicReference<>();
    AtomicReference<Denied> bindingFailure=new AtomicReference<>();
    var result=credentials.consume(purpose,token,password,trustedIp,(c,user,epoch,transition)->{
      try {
        now(c);consumePreauth(c,cookie,context,csrf);
        if(purpose==Purpose.RESET) {
          accountReset(c,transition,idempotencyKey,requireHandle(cookie),context,csrf,user,epoch);
          // The account lock is already held. Serialize all canonical denies in
          // stable order, then remove backing rows within the same transaction.
          try(var s=c.prepareStatement("SELECT cookie_digest FROM identity.auth_session WHERE user_id=? AND realm_id=? AND state='ACTIVE' ORDER BY session_ref FOR UPDATE")) {
            s.setObject(1,user);s.setObject(2,realm);
            try(var r=s.executeQuery()){while(r.next())terminal(c,lookup(c,r.getBytes(1),false),"REVOKING");}
          }
          outcome.set(new Consumed("PENDING_FENCES",transition));
        } else outcome.set(new Consumed("ACTIVATED",null));
      }catch(Denied denied){bindingFailure.set(denied);throw denied;}
    });
    if(bindingFailure.get()!=null)throw bindingFailure.get();
    if(!result.accepted())throw new Denied(switch(result.code()){case "THROTTLED"->429;case "UNAVAILABLE"->503;default->401;},result.code());
    return outcome.get();
  }

  /**
   * Cấp INT01 từ exact request đã parse lại tại boundary, không nhận Request tự chế.
   * Lock account → session và đọc lại sau lock để logout/reset đang chạy không thể
   * dùng snapshot cũ. Expiry/local deny phải commit dù response sau đó là lỗi.
   * Không network/KDF trong transaction; caller là service identity đã xác minh mTLS.
   */
  public Admission admit(byte[] exactRequest,String verifiedCaller,String configuredOrigin) throws SQLException {
    var request=CanonicalAdmissionWire.parse(exactRequest,verifiedCaller,configuredOrigin);
    byte[] key=requireHandle(request.credential());
    try(Connection c=transaction()) {
      try {
        // Tổng transaction hữu hạn, ngoài timeout từng statement/lock. PostgreSQL18
        // sẽ rollback khi vượt 6s; receiver vẫn phải kiểm budget riêng trước commit.
        try(var s=c.createStatement()){s.execute("SET LOCAL transaction_timeout='6s'");}
        now(c);Guard initial=lookup(c,key,false);
        if(initial==null)throw new Denied(401,"SESSION_INVALID");
        boolean account=lockAccount(c,initial.user,initial.epoch);
        Guard guard=lookup(c,key,true);
        if(guard==null)throw new Denied(401,"SESSION_INVALID");
        guard=enforce(c,key,guard,account);
        if(!guard.state.equals("ACTIVE")){c.commit();throw new Denied(401,"SESSION_INVALID");}
        requireContext(guard.context,request.context());
        if(request.mutation())requireCsrf(guard.csrf,request.csrfToken());

        // Đọc clock lần cuối dưới lock, không lấy timestamp từ caller. Content GET
        // không gia hạn idle; chỉ registered Learning draft-save hợp lệ mới touch.
        Instant checkedTime=now(c);
        if(!checkedTime.isBefore(guard.issued.plus(ABSOLUTE)) || !checkedTime.isBefore(guard.interactive.plus(IDLE))) {
          // Phiên có thể hết hạn giữa hai clock reads; tuyệt đối không hồi sinh idle.
          enforce(c,key,guard,account);c.commit();throw new Denied(401,"SESSION_INVALID");
        }
        Instant time=checkedTime.truncatedTo(ChronoUnit.MILLIS);
        Instant absolute=guard.issued.plus(ABSOLUTE).truncatedTo(ChronoUnit.MILLIS);
        Instant idle=(request.mutation()?time:guard.interactive).plus(IDLE).truncatedTo(ChronoUnit.MILLIS);
        Instant until=time.plusSeconds(15);
        if(idle.isBefore(until))until=idle;
        if(absolute.isBefore(until))until=absolute;
        // Không cấp admission mà receiver chắc chắn thiếu 6s transaction + margin.
        // Nếu deny budget, rollback cả idle touch; không biến poll thành heartbeat.
        if(!until.isAfter(time.plusMillis(6500)))throw new Denied(403,"ADMISSION_TIME_DENIED");
        if(request.mutation())try(var s=c.prepareStatement("UPDATE identity.auth_session SET last_interactive_at=? WHERE session_ref=?")) {
          // DB giữ precision gốc; chỉ wire deadline floor millisecond. Lưu floor
          // có thể đi lùi so với last_interactive_at trong cùng millisecond.
          s.setObject(1,utc(checkedTime));s.setObject(2,guard.ref);s.executeUpdate();
        }
        var admission=new Admission(UUID.randomUUID(),request.nonce(),request.caller(),realm,guard.user,
            guard.ref,guard.context,guard.epoch,request.action(),request.binding(),time,until,idle,absolute,request.mutation());
        c.commit();return admission;
      }catch(SQLException|RuntimeException e){c.rollback();throw e;}
    }
  }

  /** This is not INT-01/admission. An explicit user action may touch idle only
   * after a server route has checked its own policy; callers cannot send an interactive flag. */
  public View interactive(String cookie,UUID context,String csrf) throws SQLException {
    byte[] key=requireHandle(cookie);
    try(Connection c=transaction()) {
      try {
        now(c);Guard initial=lookup(c,key,false);if(initial==null)throw new Denied(401,"SESSION_INVALID");
        boolean account=lockAccount(c,initial.user,initial.epoch);
        Guard guard=enforce(c,key,lookup(c,key,true),account);
        if(!guard.state.equals("ACTIVE")){c.commit();throw new Denied(401,"SESSION_INVALID");}
        requireContext(guard.context,context);requireCsrf(guard.csrf,csrf);
        try(var s=c.prepareStatement("UPDATE identity.auth_session SET last_interactive_at=clock_timestamp() WHERE session_ref=?")){s.setObject(1,guard.ref);s.executeUpdate();}
        c.commit();return new View("ACTIVE",guard.context,guard.csrf,guard.user,Long.toString(guard.epoch),null);
      }catch(SQLException|RuntimeException e){c.rollback();throw e;}
    }
  }

  public UUID logout(String cookie,UUID context,String csrf,UUID idempotencyKey) throws SQLException {
    if(idempotencyKey==null)throw new Denied(400,"IDEMPOTENCY_REQUIRED");
    byte[] key=requireHandle(cookie);
    try(Connection c=transaction()) {
      try {
        now(c);Guard initial=lookup(c,key,false);if(initial==null)throw new Denied(401,"SESSION_INVALID");
        boolean account=lockAccount(c,initial.user,initial.epoch);
        Guard guard=lookup(c,key,true);
        requireContext(guard.context,context);
        // A lost-response retry can use its old CSRF only for exactly this
        // initiating cookie/context/key, never as a live learner credential.
        UUID retry=retryLogout(c,key,context,csrf,idempotencyKey);
        if(retry!=null){c.commit();return retry;}
        guard=enforce(c,key,guard,account);
        if(!guard.state.equals("ACTIVE")){c.commit();throw new Denied(409,"AUTH_TRANSITION_PENDING");}
        requireCsrf(guard.csrf,csrf);
        UUID op=operation(c,key,guard,idempotencyKey,"LOGOUT");
        terminal(c,guard,"REVOKING");audit(c,"SESSION_LOGOUT",guard.user,"LOCAL_DENY_PENDING_FENCES");
        c.commit();return op;
      }catch(SQLException|RuntimeException e){c.rollback();throw e;}
    }
  }

  /**
   * Precheck CSRF/context trước throttle/KDF. Password check ngoài locks, rồi
   * account → session locked reread trước callback. Nếu logout/expiry thắng lúc
   * KDF chạy, không cấp proof; expiry deny/intents vẫn commit dù trả lỗi401.
   * Chỉ reauth thành công touch idle, callback lỗi rollback cả grant/idle.
   */
  public <T> T reauthenticate(String cookie,UUID context,String csrf,String password,String trustedIp,
      ReauthenticatedAction<T> action)throws SQLException {
    byte[] key=requireHandle(cookie);String login;UUID user;
    try(Connection c=transaction()) {
      try {
        now(c);Guard initial=lookup(c,key,false);if(initial==null)throw new Denied(401,"SESSION_INVALID");
        boolean account=lockAccount(c,initial.user,initial.epoch);Guard guard=enforce(c,key,lookup(c,key,true),account);
        if(!guard.state.equals("ACTIVE")){c.commit();throw new Denied(401,"SESSION_INVALID");}
        requireContext(guard.context,context);requireCsrf(guard.csrf,csrf);user=guard.user;
        try(var s=c.prepareStatement("SELECT login_name FROM identity.app_user WHERE user_id=?")) {
          s.setObject(1,user);try(var r=s.executeQuery()){if(!r.next())throw new Denied(503,"AUTH_ACCOUNT_UNAVAILABLE");login=r.getString(1);}
        }
        c.commit();
      }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
    }
    var output=new AtomicReference<T>();var bindingFailure=new AtomicReference<Denied>();
    var result=verifier.verifyReauthAndApply(ds,throttle,login,password,trustedIp,(c,currentUser,epoch)->{
      try {
        Guard guard=lookup(c,key,true);
        if(guard==null||!user.equals(currentUser)||!currentUser.equals(guard.user)||epoch!=guard.epoch)
          throw new Denied(401,"SESSION_INVALID");
        guard=enforce(c,key,guard,true);
        if(!guard.state.equals("ACTIVE")) {
          // Callback trở về để verifier commit local deny; không throw làm
          // rollback expiry vừa ghi. Password verified không là proof issued.
          bindingFailure.set(new Denied(401,"SESSION_INVALID"));audit(c,"REAUTH_DENIED",currentUser,"SESSION_INVALID","DENIED");return;
        }
        requireContext(guard.context,context);requireCsrf(guard.csrf,csrf);Instant time=now(c);
        output.set(action.apply(c,new VerifiedSession(currentUser,guard.ref,guard.context,epoch,realm,time)));
        try(var s=c.prepareStatement("UPDATE identity.auth_session SET last_interactive_at=GREATEST(last_interactive_at,?) WHERE session_ref=?")) {
          s.setObject(1,utc(time));s.setObject(2,guard.ref);s.executeUpdate();
        }
      }catch(Denied denied){bindingFailure.set(denied);throw denied;}
    });
    if(bindingFailure.get()!=null)throw bindingFailure.get();
    if(!result.accepted())throw new Denied(switch(result.code()){case "THROTTLED"->429;case "UNAVAILABLE"->503;default->401;},result.code());
    if(output.get()==null)throw new Denied(503,"REAUTH_UNAVAILABLE");
    return output.get();
  }

  /** No principal, CSRF, idle touch or cookie emission. Unknown/unowned all 404. */
  public String operationStatus(String cookie,UUID context,UUID operation) throws SQLException {
    byte[] key=digest(cookie);
    if(key==null||context==null||operation==null)throw new Denied(404,"OPERATION_NOT_FOUND");
    try(Connection c=ds.getConnection()) {
      now(c);
      try(var s=c.prepareStatement("SELECT state FROM identity.auth_operation WHERE operation_id=? AND initiating_cookie_digest=? AND initiating_context_id=? AND realm_id=?")) {
        s.setObject(1,operation);s.setBytes(2,key);s.setObject(3,context);s.setObject(4,realm);
        try(var r=s.executeQuery()){if(!r.next())throw new Denied(404,"OPERATION_NOT_FOUND");return r.getString(1);}
      }
    }
  }

  private void requirePreauth(String cookie,UUID context,String csrf) throws SQLException {
    try(Connection c=ds.getConnection()){now(c);preauth(c,cookie,context,csrf,false);}
  }
  private void consumePreauth(Connection c,String cookie,UUID context,String csrf) throws SQLException {
    preauth(c,cookie,context,csrf,true);
    try(var s=c.prepareStatement("UPDATE identity.auth_preauth SET consumed_at=clock_timestamp(),csrf_token=NULL WHERE cookie_digest=? AND consumed_at IS NULL AND expires_at>clock_timestamp()")) {
      s.setBytes(1,requireHandle(cookie));if(s.executeUpdate()!=1)throw new Denied(409,"PREAUTH_CONSUMED");
    }
  }
  private void preauth(Connection c,String cookie,UUID context,String csrf,boolean lock) throws SQLException {
    try(var s=c.prepareStatement("SELECT context_id,csrf_token,consumed_at IS NULL AND expires_at>clock_timestamp() FROM identity.auth_preauth WHERE cookie_digest=? AND realm_id=?"+(lock?" FOR UPDATE":""))) {
      s.setBytes(1,requireHandle(cookie));s.setObject(2,realm);
      try(var r=s.executeQuery()) {
        if(!r.next())throw new Denied(403,"CSRF_DENIED");
        requireContext(r.getObject(1,UUID.class),context);
        if(!r.getBoolean(3))throw new Denied(409,"PREAUTH_CONSUMED");
        requireCsrf(r.getString(2),csrf);
      }
    }
  }
  private Guard enforce(Connection c,byte[] key,Guard guard,boolean account) throws SQLException {
    Instant time=now(c);
    if(guard.state.equals("ACTIVE") && (!account || !guard.realm.equals(realm) ||
        !time.isBefore(guard.issued.plus(ABSOLUTE)) || !time.isBefore(guard.interactive.plus(IDLE)) ||
        repository(c).findById(guard.framework)==null)) {
      // No background heartbeat/sliding expiry. Canonical expiry and intents
      // commit even if the caller will subsequently receive an auth error.
      operation(c,key,guard,UUID.randomUUID(),"EXPIRY");terminal(c,guard,"EXPIRED");
      audit(c,"SESSION_EXPIRED",guard.user,"LOCAL_DENY_PENDING_FENCES");
      return lookup(c,key,true);
    }
    return guard;
  }
  private static boolean lockAccount(Connection c,UUID user,long epoch) throws SQLException {
    try(var s=c.prepareStatement("SELECT state='ACTIVE' AND NOT operator_disabled AND NOT auth_transition_pending AND credential_epoch=? FROM identity.app_user WHERE user_id=? FOR UPDATE")) {
      s.setLong(1,epoch);s.setObject(2,user);try(var r=s.executeQuery()){return r.next() && r.getBoolean(1);}
    }
  }
  private Guard lookup(Connection c,byte[] key,boolean lock) throws SQLException {
    try(var s=c.prepareStatement("SELECT session_ref,context_id,user_id,credential_epoch,realm_id,state,csrf_token,issued_at,last_interactive_at,framework_id FROM identity.auth_session WHERE cookie_digest=?"+(lock?" FOR UPDATE":""))) {
      s.setBytes(1,key);try(var r=s.executeQuery()) {
        return !r.next()?null:new Guard(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getLong(4),r.getObject(5,UUID.class),r.getString(6),r.getString(7),r.getObject(8,OffsetDateTime.class).toInstant(),r.getObject(9,OffsetDateTime.class).toInstant(),r.getString(10));
      }
    }
  }
  private UUID operation(Connection c,byte[] key,Guard g,UUID idempotency,String action) throws SQLException {
    UUID op=UUID.randomUUID();byte[] command=hash((action+"\n"+g.user+"\n"+g.ref+"\n"+g.epoch+"\n"+realm+"\nr1-static-v1\ncontent,learning").getBytes(StandardCharsets.UTF_8));
    try(var s=c.prepareStatement("INSERT INTO identity.auth_operation(operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,target_session_ref,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants) VALUES (?,?,?,?,?,?,?,?,?,'SESSION',?,?,'r1-static-v1',ARRAY['content','learning'])")) {
      s.setObject(1,op);s.setObject(2,idempotency);s.setBytes(3,key);s.setObject(4,g.context);s.setBytes(5,digest(g.csrf));s.setObject(6,g.user);s.setObject(7,g.ref);s.setLong(8,g.epoch);s.setObject(9,realm);s.setString(10,action);s.setBytes(11,command);s.executeUpdate();
    }
    fenceIntents(c,op,command);
    // Appending exact hai auth outboxes dùng cùng Connection/transaction với
    // operation và canonical deny phía caller; không gửi network từ callback này.
    if(barrier!=null)barrier.append(c,op);
    return op;
  }
  private void accountReset(Connection c,UUID op,UUID idempotency,byte[] key,UUID context,String csrf,UUID user,long epoch) throws SQLException {
    byte[] command=hash(("RESET\n"+user+"\nACCOUNT\n"+epoch+"\n"+realm+"\nr1-static-v1\ncontent,learning").getBytes(StandardCharsets.UTF_8));
    try(var s=c.prepareStatement("INSERT INTO identity.auth_operation(operation_id,idempotency_key,initiating_cookie_digest,initiating_context_id,initiating_csrf_digest,target_user_id,credential_epoch,realm_id,scope,action,command_digest,topology_version,required_participants) VALUES (?,?,?,?,?,?,?,?,'ACCOUNT','RESET',?,'r1-static-v1',ARRAY['content','learning'])")) {
      s.setObject(1,op);s.setObject(2,idempotency);s.setBytes(3,key);s.setObject(4,context);s.setBytes(5,digest(csrf));s.setObject(6,user);s.setLong(7,epoch);s.setObject(8,realm);s.setBytes(9,command);s.executeUpdate();
    }
    fenceIntents(c,op,command);
    if(barrier!=null)barrier.append(c,op);
  }
  private static void fenceIntents(Connection c,UUID op,byte[] command) throws SQLException {
    for(String participant:new String[]{"content","learning"})try(var s=c.prepareStatement("INSERT INTO identity.auth_fence_intent(operation_id,participant,command_digest) VALUES (?,?,?)")) {
      s.setObject(1,op);s.setString(2,participant);s.setBytes(3,command);s.executeUpdate();
    }
  }
  private static void terminal(Connection c,Guard g,String state) throws SQLException {
    try(var s=c.prepareStatement("UPDATE identity.auth_session SET state=?,csrf_token=NULL WHERE session_ref=?")){s.setString(1,state);s.setObject(2,g.ref);s.executeUpdate();}
    // Removing backing attributes/security context is atomic with canonical deny.
    try(var s=c.prepareStatement("DELETE FROM identity.spring_session WHERE session_id=?")){s.setString(1,g.framework);s.executeUpdate();}
  }
  private UUID retryLogout(Connection c,byte[] key,UUID context,String csrf,UUID idempotency) throws SQLException {
    try(var s=c.prepareStatement("SELECT operation_id,action,initiating_csrf_digest FROM identity.auth_operation WHERE initiating_cookie_digest=? AND initiating_context_id=? AND idempotency_key=? AND realm_id=?")) {
      s.setBytes(1,key);s.setObject(2,context);s.setObject(3,idempotency);s.setObject(4,realm);
      try(var r=s.executeQuery()) {
        if(!r.next())return null;
        if(!r.getString(2).equals("LOGOUT"))throw new Denied(409,"IDEMPOTENCY_CONFLICT");
        byte[] supplied=digest(csrf);if(supplied==null||!MessageDigest.isEqual(r.getBytes(3),supplied))throw new Denied(403,"CSRF_DENIED");
        return r.getObject(1,UUID.class);
      }
    }
  }
  private UUID pendingOperation(Connection c,byte[] key) throws SQLException {
    try(var s=c.prepareStatement("SELECT operation_id FROM identity.auth_operation WHERE initiating_cookie_digest=? AND realm_id=? AND state='PENDING_FENCES' ORDER BY created_at DESC,operation_id DESC LIMIT 1")) {
      s.setBytes(1,key);s.setObject(2,realm);try(var r=s.executeQuery()){return r.next()?r.getObject(1,UUID.class):null;}
    }
  }
  /** Gọi dưới account/session locks; COMPLETED đã qua guarded dual-ACK trigger.
   * Chỉ fence đúng session hoặc account floor bao phủ epoch này mới cho flow mới;
   * một operation khác của cùng user không đủ mở cookie terminal chưa có barrier. */
  private boolean completedFence(Connection c,Guard guard) throws SQLException {
    try(var s=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM identity.auth_operation WHERE realm_id=? AND target_user_id=? AND state='COMPLETED' AND ((scope='SESSION' AND target_session_ref=? AND credential_epoch=?) OR (scope='ACCOUNT' AND credential_epoch>=?)))")) {
      s.setObject(1,realm);s.setObject(2,guard.user);s.setObject(3,guard.ref);s.setLong(4,guard.epoch);s.setLong(5,guard.epoch);
      try(var r=s.executeQuery()){r.next();return r.getBoolean(1);}
    }
  }
  private UUID operationContext(Connection c,UUID op) throws SQLException {
    try(var s=c.prepareStatement("SELECT initiating_context_id FROM identity.auth_operation WHERE operation_id=?")){s.setObject(1,op);try(var r=s.executeQuery()){r.next();return r.getObject(1,UUID.class);}}
  }
  private Connection transaction() throws SQLException {
    Connection c=ds.getConnection();
    try {c.setAutoCommit(false);try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='2s'");s.execute("SET LOCAL statement_timeout='5s'");}return c;}
    catch(SQLException|RuntimeException e){c.close();throw e;}
  }
  private Instant now(Connection c) throws SQLException {
    Instant before=Instant.now();
    try(var s=c.createStatement()) {
      s.setQueryTimeout(1);
      try(var r=s.executeQuery("SELECT clock_timestamp(),current_database(),current_user,realm_id,topology_version FROM identity.auth_environment WHERE singleton")) {
        if(!r.next()||!r.getString(2).equals("identity_db")||!r.getString(3).equals("identity_app")||!realm.equals(r.getObject(4,UUID.class))||!r.getString(5).equals("r1-static-v1"))throw new Denied(503,"AUTH_ENVIRONMENT_UNAVAILABLE");
        Instant time=r.getObject(1,OffsetDateTime.class).toInstant(),after=Instant.now();
        if(after.isBefore(before)||Duration.between(before,after).toMillis()>1000||time.isBefore(before.minusMillis(500))||time.isAfter(after.plusMillis(500)))throw new Denied(503,"CLOCK_UNAVAILABLE");
        return time;
      }
    }
  }
  static SessionRepository<Session> repository(Connection c) {return repository(c,null);}
  @SuppressWarnings("unchecked")
  private static SessionRepository<Session> repository(Connection c,String generatedId) {
    // No @EnableJdbcHttpSession/SessionRepositoryFilter: those could emit
    // automatic/sliding Set-Cookie. Canonical routes alone own cookie issuance.
    var repository=new JdbcIndexedSessionRepository(new JdbcTemplate(new SingleConnectionDataSource(c,true)),TransactionOperations.withoutTransaction());
    repository.setTableName("identity.spring_session");
    if(generatedId!=null)repository.setSessionIdGenerator(()->generatedId);
    // JdbcSession is package-private. Its supported public contract is Session;
    // each returned Session is created/read by exactly this repository.
    return (SessionRepository<Session>)(SessionRepository<?>)repository;
  }
  private static void audit(Connection c,String event,UUID user,String detail) throws SQLException {
    audit(c,event,user,detail,"SUCCEEDED");
  }
  /** Audit ghi kết quả của hành động xin quyền, không nhầm việc commit deny
   * thành reauth thành công. Cùng transaction nên rollback cũng xóa audit. */
  private static void audit(Connection c,String event,UUID user,String detail,String result) throws SQLException {
    try(var s=c.prepareStatement("INSERT INTO identity.security_audit(audit_id,event_type,target_user_id,result,correlation_id,detail_code) VALUES (?,?,?,?,?,?)")) {
      s.setObject(1,UUID.randomUUID());s.setString(2,event);s.setObject(3,user);s.setString(4,result);s.setObject(5,UUID.randomUUID());s.setString(6,detail);s.executeUpdate();
    }
  }
  private String secret(){byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
  public static byte[] digest(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{43}")?hash(value.getBytes(StandardCharsets.US_ASCII)):null;}
  private static byte[] hash(byte[] value){try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
  private static byte[] requireHandle(String value){byte[] key=digest(value);if(key==null)throw new Denied(401,"SESSION_INVALID");return key;}
  private static void requireContext(UUID expected,UUID actual){if(!expected.equals(actual))throw new Denied(409,"SESSION_CONTEXT_MISMATCH");}
  private static void requireCsrf(String expected,String actual){byte[] e=digest(expected),a=digest(actual);if(e==null||a==null||!MessageDigest.isEqual(e,a))throw new Denied(403,"CSRF_DENIED");}
  private static OffsetDateTime utc(Instant instant){return instant.atOffset(java.time.ZoneOffset.UTC);}
  private static View anonymous(){return new View("ANONYMOUS",null,null,null,null,null);}
}
