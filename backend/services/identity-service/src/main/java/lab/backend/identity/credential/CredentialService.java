package lab.backend.identity.credential;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Credential application boundary; browser preauth/CSRF is added by R04. */
public final class CredentialService {
  private final CredentialWorkflow workflow;
  private final PasswordCorpus corpus;
  private final AuthThrottle throttle;
  private final Semaphore slots=new Semaphore(2);
  public CredentialService(CredentialWorkflow workflow,PasswordCorpus corpus,AuthThrottle throttle) {
    this.workflow=workflow;this.corpus=corpus;this.throttle=throttle;
  }
  public CredentialVerifier.Result consume(CredentialWorkflow.Purpose purpose,String token,String password,String trustedIp) {
    return consume(purpose,token,password,trustedIp,(c,user,epoch,transitionId)->{});
  }
  public CredentialVerifier.Result consume(CredentialWorkflow.Purpose purpose,String token,String password,String trustedIp,
      CredentialWorkflow.ConsumedAction action) {
    try {PasswordPolicy.requireValid(password);corpus.requireAllowed(password);trustedIp=CredentialVerifier.canonicalIp(trustedIp);}
    catch(IllegalArgumentException invalid){return CredentialVerifier.Result.denied();}
    if(token==null||token.length()>128)return CredentialVerifier.Result.denied();
    var keys=List.of(new AuthThrottle.Key(purpose==CredentialWorkflow.Purpose.ACTIVATE?AuthThrottle.Kind.ACTIVATE:AuthThrottle.Kind.RESET,token),new AuthThrottle.Key(AuthThrottle.Kind.IP,trustedIp));
    boolean reserved=false,slot=false,failed=false;
    try {
      if(throttle.reserve(keys)!=AuthThrottle.Decision.ALLOW)return CredentialVerifier.Result.throttled();
      reserved=true;slot=slots.tryAcquire();
      if(!slot)return CredentialVerifier.Result.throttled();
      boolean success=workflow.consume(purpose,token,password,corpus,action);failed=!success;
      return success?CredentialVerifier.Result.verified():CredentialVerifier.Result.denied();
    }catch(SQLException|RuntimeException failure){return CredentialVerifier.Result.unavailable();}
    finally{if(slot)slots.release();if(reserved)try{throttle.complete(keys,failed);}catch(SQLException unavailable){/* conservative TTL */}}
  }
}
