package lab.backend.identity.credential;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.postgresql.ds.PGSimpleDataSource;

/** Operator process entry point; secret inputs use stdin/environment, never argv. */
public final class IdentityMaintenanceCli {
  private IdentityMaintenanceCli() {}
  public static void main(String[] args) {
    try { execute(args); }
    catch(Exception failure) {System.err.println("MAINTENANCE_FAILED");System.exit(1);}
  }
  static void execute(String[] args) throws Exception {
    if(args.length!=2)throw new IllegalArgumentException("Invalid command");
    var corpus=PasswordCorpus.load(Path.of(required("IDENTITY_CORPUS_PATH")),required("IDENTITY_CORPUS_SHA256"));
    byte[] key=Base64.getDecoder().decode(required("IDENTITY_THROTTLE_KEY"));
    PGSimpleDataSource ds=new PGSimpleDataSource();
    ds.setURL(required("IDENTITY_DB_URL"));ds.setUser(required("IDENTITY_DB_USER"));ds.setPassword(required("IDENTITY_DB_PASSWORD"));
    ds.setConnectTimeout(1);ds.setSocketTimeout(5);
    try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_database(),current_user,to_regclass('identity.credential_transition')")) {
      r.next();if(!r.getString(1).equals("identity_db")||!r.getString(2).equals("identity_app")||r.getString(3)==null)throw new IllegalStateException("Own migrated database required");
    }
    var throttle=new AuthThrottle(ds,key,100000);
    var workflow=new CredentialWorkflow(ds);
    switch(args[0]) {
      case "invite" -> print(workflow.invite(args[1]));
      case "issue-reset" -> print(workflow.reissue(UUID.fromString(args[1]),CredentialWorkflow.Purpose.RESET));
      case "reissue-activation" -> print(workflow.reissue(UUID.fromString(args[1]),CredentialWorkflow.Purpose.ACTIVATE));
      case "disable" -> changed(workflow,args[1],CredentialWorkflow.AccountState.DISABLED);
      case "enable" -> changed(workflow,args[1],CredentialWorkflow.AccountState.ACTIVE);
      case "require-recovery" -> changed(workflow,args[1],CredentialWorkflow.AccountState.RECOVERY_REQUIRED);
      case "verify" -> {
        String password=readSecrets(1)[0];
        System.out.println(new CredentialVerifier(new Argon2CredentialHasher()).verifyAndApply(ds,throttle,args[1],password,"127.0.0.1",(c,u,e)->{}).code());
      }
      case "activate", "reset" -> {
        if(!args[1].equals("stdin"))throw new IllegalArgumentException("Secrets require stdin");
        String[] secrets=readSecrets(2);
        var purpose=args[0].equals("activate")?CredentialWorkflow.Purpose.ACTIVATE:CredentialWorkflow.Purpose.RESET;
        System.out.println(new CredentialService(workflow,corpus,throttle).consume(purpose,secrets[0],secrets[1],"127.0.0.1").code());
      }
      default -> throw new IllegalArgumentException("Invalid command");
    }
  }
  private static String[] readSecrets(int count) throws Exception {
    byte[] bytes=System.in.readNBytes(2049);
    if(bytes.length>2048)throw new IllegalArgumentException("Credential input too large");
    String[] parts=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().split("\n",-1);
    if(parts.length!=count+1||!parts[count].isEmpty())throw new IllegalArgumentException("Invalid credential input");
    return java.util.Arrays.copyOf(parts,count);
  }
  private static void changed(CredentialWorkflow workflow,String id,CredentialWorkflow.AccountState state) throws Exception {
    workflow.setState(UUID.fromString(id),state);System.out.println("ACCOUNT_TRANSITION_PENDING");
  }
  private static void print(CredentialWorkflow.IssuedToken token) {
    System.out.println("userId="+token.userId());System.out.println("purpose="+token.purpose());System.out.println("token="+token.rawToken());
    System.err.println("Token emitted once for the verified operator channel.");
  }
  private static String required(String name) {String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalStateException("Missing operator configuration");return value;}
}
