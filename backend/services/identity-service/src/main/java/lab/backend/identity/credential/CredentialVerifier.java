package lab.backend.identity.credential;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;

/** Kiểm mật khẩu ngoài locks rồi đọc lại account snapshot trước effect. Login,
 * reauth và chuẩn bị mật khẩu mới dùng chung hai KDF slots của singleton này. */
public final class CredentialVerifier {
  public record Result(boolean accepted,String code,String message) {
    static Result denied(){return new Result(false,"INVALID_CREDENTIALS","Credentials could not be verified");}
    static Result unavailable(){return new Result(false,"UNAVAILABLE","Credentials could not be verified");}
    static Result throttled(){return new Result(false,"THROTTLED","Try again later");}
    static Result verified(){return new Result(true,"VERIFIED","Credential verified");}
  }
  @FunctionalInterface public interface VerifiedAction { void apply(Connection transaction,UUID user,long epoch) throws SQLException; }
  private final PasswordHasher hasher;
  private final String dummyHash;
  private final Semaphore kdfSlots=new Semaphore(2);
  public CredentialVerifier(Argon2CredentialHasher hasher){this((PasswordHasher)hasher);}
  CredentialVerifier(PasswordHasher hasher){this.hasher=hasher;this.dummyHash=new Argon2CredentialHasher().hash("dummy credential value");}

  public Result verify(String password,String storedHash,String state) {
    try {
      boolean matched=hasher.matches(password,storedHash==null?dummyHash:storedHash);
      return matched&&storedHash!=null&&"ACTIVE".equals(state)?Result.verified():Result.denied();
    } catch(IllegalArgumentException invalid) {return Result.denied();}
      catch(RuntimeException failure) {return Result.unavailable();}
  }

  public Result verifyAndApply(DataSource ds,AuthThrottle throttle,String login,String password,
      String trustedIp,VerifiedAction action) {
    return verifyAndApply(ds,throttle,login,password,trustedIp,action,AuthThrottle.Kind.ACCOUNT);
  }

  /** Reuse singleton KDF slots và locked credential snapshot của login, nhưng
   * failed reauth dùng bucket riêng. Caller phải precheck canonical CSRF/context;
   * callback đọc lại session dưới account lock trước khi tạo proof/effect. */
  public Result verifyReauthAndApply(DataSource ds,AuthThrottle throttle,String login,String password,
      String trustedIp,VerifiedAction action) {
    return verifyAndApply(ds,throttle,login,password,trustedIp,action,AuthThrottle.Kind.REAUTH);
  }

  /** Hash mật khẩu mới ngoài transaction, dùng cùng hai KDF slots với login và
   * reauth. Caller phải đã kiểm proof/CSRF, rồi locked-reread sau khi hash xong;
   * hash này không tự cấp quyền hoặc đổi dữ liệu. Không log/serialize Prepared. */
  public record Prepared(Result result,String hash) {
    @Override public String toString(){return "PreparedCredential[REDACTED]";}
  }
  public Prepared preparePassword(String password,PasswordCorpus corpus) {
    try{PasswordPolicy.requireValid(password);Objects.requireNonNull(corpus).requireAllowed(password);}
    catch(IllegalArgumentException invalid){return new Prepared(Result.denied(),null);}
    if(!kdfSlots.tryAcquire())return new Prepared(Result.throttled(),null);
    try{return new Prepared(Result.verified(),hasher.hash(password));}
    catch(RuntimeException unavailable){return new Prepared(Result.unavailable(),null);}
    finally{kdfSlots.release();}
  }

  private Result verifyAndApply(DataSource ds,AuthThrottle throttle,String login,String password,
      String trustedIp,VerifiedAction action,AuthThrottle.Kind kind) {
    String normalized;
    try{normalized=CredentialWorkflow.normalizeLoginName(login);PasswordPolicy.requireValid(password);trustedIp=canonicalIp(trustedIp);}
    catch(IllegalArgumentException invalid){return Result.denied();}
    var keys=List.of(new AuthThrottle.Key(kind,normalized),new AuthThrottle.Key(AuthThrottle.Kind.IP,trustedIp));
    boolean reserved=false,slot=false,failed=false;
    Result result=Result.unavailable();
    try {
      if(throttle.reserve(keys)!=AuthThrottle.Decision.ALLOW)return Result.throttled();
      reserved=true;
      slot=kdfSlots.tryAcquire();
      if(!slot)return Result.throttled();
      CredentialWorkflow.Account snapshot=null;
      try(Connection c=ds.getConnection();var s=c.prepareStatement("SELECT user_id,state,password_hash,credential_epoch,auth_transition_pending,operator_disabled FROM identity.app_user WHERE login_name=?")) {
        s.setString(1,normalized);
        try(var r=s.executeQuery()){if(r.next())snapshot=new CredentialWorkflow.Account(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getBoolean(5),r.getBoolean(6));}
      }
      result=verify(password,snapshot==null?null:snapshot.hash(),snapshot==null?null:snapshot.state());
      if(snapshot==null||!result.accepted()){
        failed=result.code().equals("INVALID_CREDENTIALS");
        try(Connection c=ds.getConnection()) {
          CredentialWorkflow.audit(c,"CREDENTIAL_CHECK",snapshot==null?null:snapshot.user(),failed?"DENIED":"FAILED",failed?"INVALID_CREDENTIALS":"UNAVAILABLE");
        }
        return result;
      }
      try(Connection c=ds.getConnection()) {
        c.setAutoCommit(false);
        try {
          try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='2s'");s.execute("SET LOCAL statement_timeout='5s'");}
          // Reauth callback có grant/idle effect: budget toàn transaction không
          // được nhân lên theo số statement; KDF đã chạy trước transaction này.
          if(kind==AuthThrottle.Kind.REAUTH)try(var s=c.createStatement()){s.execute("SET LOCAL transaction_timeout='6s'");}
          var current=CredentialWorkflow.lockUser(c,snapshot.user());
          if(current.epoch()!=snapshot.epoch()||!Objects.equals(current.hash(),snapshot.hash())||!current.state().equals("ACTIVE")||current.pending()||current.disabled()) {
            c.rollback();
            CredentialWorkflow.audit(c,"CREDENTIAL_CHECK",current.user(),"DENIED","INVALID_CREDENTIALS");
            c.commit();failed=true;result=Result.denied();return result;
          }
          action.apply(c,current.user(),current.epoch());
          CredentialWorkflow.audit(c,"CREDENTIAL_CHECK",current.user(),"SUCCEEDED","CURRENT_SNAPSHOT");
          c.commit();result=Result.verified();return result;
        } catch(SQLException|RuntimeException e){c.rollback();throw e;}
      }
    } catch(SQLException|RuntimeException failure) {result=Result.unavailable();return result;}
    finally {
      if(slot)kdfSlots.release();
      if(reserved)try{throttle.complete(keys,failed);}catch(SQLException unavailable){/* DB failure keeps conservative reservations until TTL. */}
    }
  }
  static void requireIp(String value) {
    canonicalIp(value);
  }
  static String canonicalIp(String value) {
    // Caller must supply a resolved trusted source, never arbitrary forwarded headers.
    if(value==null||value.length()>45||!(value.matches("[0-9.]+")||value.matches("[0-9a-fA-F:]+")))
      throw new IllegalArgumentException("Trusted source IP required");
    try {return java.net.InetAddress.getByName(value).getHostAddress();}
    catch(java.net.UnknownHostException invalid){throw new IllegalArgumentException("Trusted source IP required");}
  }
}
