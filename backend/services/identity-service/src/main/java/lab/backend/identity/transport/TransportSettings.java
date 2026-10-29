package lab.backend.identity.transport;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Cấu hình own-service đã kiểm; topology lab có tên cố định, không nhận port
 * endpoint tùy ý. Constructor cũ giữ profile qualification một service riêng. */
public record TransportSettings(Path directory,Path keyStore,Path trustStore,String storePassword,int dbPort,String dbPassword,String dbRunId,String realm,String generation,long clockOffsetMs,String listenerTopology) {
  public static final String SERVICE="identity";
  public static final String FOUNDATION="r02b-single-service-v1",CANONICAL="r04d-three-service-loopback-v1";
  public TransportSettings {
    if(!java.util.Set.of(FOUNDATION,CANONICAL).contains(listenerTopology==null?"":listenerTopology))throw new IllegalArgumentException("Named owned topology required");
    if(CANONICAL.equals(listenerTopology)&&clockOffsetMs!=0)throw new IllegalArgumentException("Canonical topology denies clock override");
  }
  public TransportSettings(Path directory,Path keyStore,Path trustStore,String storePassword,int dbPort,String dbPassword,String dbRunId,String realm,String generation,long clockOffsetMs) {
    this(directory,keyStore,trustStore,storePassword,dbPort,dbPassword,dbRunId,realm,generation,clockOffsetMs,FOUNDATION);
  }
  public boolean canonicalLab(){return CANONICAL.equals(listenerTopology);}
  public int publicPort(){return canonicalLab()?namedPublicPort(SERVICE):8080;}
  public int privatePort(){return peerPrivatePort(SERVICE);}
  /** Mọi peer vẫn loopback + pinned certificate identity; mapping static không
   * thay required participants hoặc participant DB generation của auth barrier. */
  public int peerPrivatePort(String peer){
    int publicPort=namedPublicPort(peer);return canonicalLab()?publicPort+360:8443;
  }
  private static int namedPublicPort(String service){
    return switch(service){case "identity"->18081;case "content"->18082;case "learning"->18083;default->throw new IllegalArgumentException("Unknown topology service");};
  }
  public static TransportSettings load(ConfigurableEnvironment env){
    boolean canonical=java.util.Arrays.equals(env.getActiveProfiles(),new String[]{"canonical"});
    int publicPort=canonical?namedPublicPort(SERVICE):8080;
    if(!"servlet".equals(env.getProperty("spring.main.web-application-type","servlet")) || env.containsProperty("management.server.port") || !"none".equals(env.getProperty("server.forward-headers-strategy","none")) || !"127.0.0.1".equals(env.getProperty("server.address","127.0.0.1")) || env.getProperty("server.port",Integer.class,publicPort)!=publicPort)throw new IllegalStateException("Transport listener configuration denied");
    for(String key:new String[]{"server.ssl.enabled","server.ssl.client-auth","server.ssl.key-store","server.ssl.trust-store","server.ssl.bundle"})if(env.containsProperty(key))throw new IllegalStateException("Transport TLS overrides denied");
    String input=env.getProperty("backendlab.trust.config");if(input==null || input.isBlank())throw new IllegalStateException("Transport config missing; no protected readiness");
    try{
      Path config=Path.of(input).toRealPath();Path directory=config.getParent();Path workspace=Path.of(System.getProperty("user.dir")).toRealPath();
      // Maven and packaged smoke use different cwd; marker supplies the repo,
      // then the real config path must be an immediate owned lab child.
      var marker=WireCodec.JSON.readTree(Files.readAllBytes(directory.resolve("ownership.json")));
      workspace=Path.of(marker.get("workspace").asString()).toRealPath();
      Path expectedRoot=workspace.resolve(".tools/trust-lab");
      if(!expectedRoot.toRealPath().equals(expectedRoot) || !directory.getParent().equals(expectedRoot) || !directory.getFileName().toString().startsWith("run-"))throw new Exception();
      UUID.fromString(marker.get("runId").asString());
      Properties p=new Properties();try(Reader reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){p.load(reader);}
      var fields=new java.util.HashSet<>(java.util.Set.of("schemaVersion","service","environment","runId","realm","generation","keyStore","trustStore","storePassword","dbPort","dbPassword","dbRunId","test.clockOffsetMs"));
      if(canonical)fields.add("listenerTopology");
      if(!p.stringPropertyNames().equals(fields)||(canonical&&!CANONICAL.equals(p.getProperty("listenerTopology"))))throw new Exception();
      if(!(canonical?"2":"1").equals(p.getProperty("schemaVersion")) || !SERVICE.equals(p.getProperty("service")) || !"lab".equals(p.getProperty("environment")) || !marker.get("runId").asString().equals(p.getProperty("runId")))throw new Exception();
      UUID.fromString(p.getProperty("realm"));UUID.fromString(p.getProperty("generation"));UUID.fromString(p.getProperty("dbRunId"));
      Path key=directory.resolve(p.getProperty("keyStore")).toRealPath(),trust=directory.resolve(p.getProperty("trustStore")).toRealPath();
      if(!key.getParent().equals(directory) || !trust.getParent().equals(directory))throw new Exception();
      String password=p.getProperty("storePassword"),dbPassword=p.getProperty("dbPassword");if(password==null || password.length()<32 || dbPassword==null || dbPassword.length()<32)throw new Exception();
      int port=Integer.parseInt(p.getProperty("dbPort"));long offset=Long.parseLong(p.getProperty("test.clockOffsetMs","0"));if(port<1024 || port>65535 || Math.abs(offset)>60000)throw new Exception();
      TransportSettings settings=new TransportSettings(directory,key,trust,password,port,dbPassword,p.getProperty("dbRunId"),p.getProperty("realm"),p.getProperty("generation"),offset,canonical?CANONICAL:FOUNDATION);
      var certificate=(X509Certificate)settings.store(key).getCertificate("leaf");
      if(!SERVICE.equals(CertificateIdentity.identity(certificate,"lab",false)) || !SERVICE.equals(CertificateIdentity.identity(certificate,"lab",true)))throw new IllegalStateException("Transport server certificate denied");
      KeyStore ca=settings.store(trust);if(ca.size()!=1 || !ca.isCertificateEntry("ca") || ((X509Certificate)ca.getCertificate("ca")).getBasicConstraints()<0)throw new IllegalStateException("Transport trust store denied");
      var chain=settings.store(key).getCertificateChain("leaf");
      if(chain==null || chain.length!=2 || !java.util.Arrays.equals(chain[1].getEncoded(),ca.getCertificate("ca").getEncoded()))throw new IllegalStateException("Transport certificate chain denied");
      ((X509Certificate)chain[1]).checkValidity();certificate.verify(chain[1].getPublicKey());
      settings.dbTime();
      env.getPropertySources().addFirst(new MapPropertySource("validated-transport",Map.ofEntries(
          Map.entry("server.address","127.0.0.1"),Map.entry("server.port",settings.publicPort()),Map.entry("server.ssl.enabled",true),
          Map.entry("server.ssl.key-store",key.toUri().toString()),Map.entry("server.ssl.key-store-password",password),Map.entry("server.ssl.key-store-type","PKCS12"),Map.entry("server.ssl.key-alias","leaf"),
          Map.entry("server.ssl.trust-store",trust.toUri().toString()),Map.entry("server.ssl.trust-store-password",password),Map.entry("server.ssl.trust-store-type","PKCS12"),
          Map.entry("server.ssl.client-auth","need"),Map.entry("server.ssl.enabled-protocols","TLSv1.2,TLSv1.3"),Map.entry("server.forward-headers-strategy","none"))));
      return settings;
    }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Transport config or certificate invalid; no protected readiness");}
  }
  public KeyStore store(Path path)throws Exception{KeyStore store=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(path)){store.load(input,storePassword.toCharArray());}return store;}
  public Instant dbTime(){
    Properties p=new Properties();p.setProperty("user",SERVICE+"_app");p.setProperty("password",dbPassword);p.setProperty("connectTimeout","1");p.setProperty("socketTimeout","1");p.setProperty("sslmode","disable");
    try(var connection=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+dbPort+"/"+SERVICE+"_db",p);var statement=connection.createStatement()){
      statement.setQueryTimeout(1);
      // Clock sample bounds the SQL round trip; connection/authentication have their own 1s budgets.
      Instant before=Instant.now().plusMillis(clockOffsetMs);
      try(var result=statement.executeQuery("SELECT clock_timestamp(),current_database(),current_user,current_setting('backend_lab.run_id')")){
        if(!result.next() || !result.getString(2).equals(SERVICE+"_db") || !result.getString(3).equals(SERVICE+"_app") || !result.getString(4).equals(dbRunId))throw new Exception();
        Instant db=result.getObject(1,java.time.OffsetDateTime.class).toInstant(),after=Instant.now().plusMillis(clockOffsetMs);
        if(after.isBefore(before) || java.time.Duration.between(before,after).toMillis()>1000 || db.isBefore(before.minusMillis(500)) || db.isAfter(after.plusMillis(500)))throw new Exception();return db;
      }
    }catch(Exception e){throw new IllegalStateException("Transport clock unavailable or outside verified budget");}
  }
  @Override public String toString(){return "TransportSettings[service="+SERVICE+",secrets=redacted]";}
}
