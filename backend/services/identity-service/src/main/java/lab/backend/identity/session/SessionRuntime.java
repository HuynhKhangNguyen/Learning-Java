package lab.backend.identity.session;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import lab.backend.identity.credential.*;
import lab.backend.identity.transport.TransportSettings;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.ConfigurableEnvironment;

/** Explicit owned lab runtime. No schema migration, realm reset or production bypass. */
public final class SessionRuntime {
  private final SessionAuthority authority;
  private final String origin;
  private SessionRuntime(SessionAuthority authority,String origin){this.authority=authority;this.origin=origin;}
  public SessionAuthority authority(){return authority;}
  public SessionHttpHandler handler(){return new SessionHttpHandler(authority,origin);}
  @Override public String toString(){return "SessionRuntime[lab,configuration=REDACTED]";}

  public static SessionRuntime load(ConfigurableEnvironment env,TransportSettings transport) {
    try {
      String input=env.getProperty("backendlab.session.config");
      if(input==null || input.isBlank())throw new Exception();
      Path file=Path.of(input).toRealPath();
      if(!file.getParent().equals(transport.directory()))throw new Exception();
      Properties p=new Properties();
      try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(reader);}
      if(!p.stringPropertyNames().equals(Set.of("schemaVersion","enabled","realm","origin","corpusPath","corpusSha256","throttleKey","dbRunId"))
          || !"1".equals(p.getProperty("schemaVersion")) || !"true".equals(p.getProperty("enabled"))
          || !transport.realm().equals(p.getProperty("realm")) || !transport.dbRunId().equals(p.getProperty("dbRunId"))
          || transport.clockOffsetMs()!=0)throw new Exception();
      UUID realm=UUID.fromString(p.getProperty("realm"));
      // Origin is validated by the adapter before any listener is created.
      String origin=p.getProperty("origin");new SessionHttpHandler(null,origin);
      Path corpusFile=Path.of(p.getProperty("corpusPath")).toRealPath();
      var corpus=PasswordCorpus.load(corpusFile,p.getProperty("corpusSha256"));
      byte[] key=Base64.getDecoder().decode(p.getProperty("throttleKey"));
      if(key.length!=32 || java.util.Arrays.equals(key,new byte[32]))throw new Exception();
      PGSimpleDataSource ds=new PGSimpleDataSource();
      ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{transport.dbPort()});
      ds.setDatabaseName("identity_db");ds.setUser("identity_app");ds.setPassword(transport.dbPassword());
      ds.setConnectTimeout(1);ds.setSocketTimeout(6);ds.setOptions("-c statement_timeout=5000 -c lock_timeout=2000");
      var throttle=new AuthThrottle(ds,key,100000);
      var authority=new SessionAuthority(ds,realm,new CredentialVerifier(new Argon2CredentialHasher()),throttle,
          new CredentialService(new CredentialWorkflow(ds),corpus,throttle));
      authority.verifyRuntime();
      return new SessionRuntime(authority,origin);
    }catch(Exception failure){throw new IllegalStateException("Session runtime configuration/schema unavailable; no protected readiness");}
  }
}
