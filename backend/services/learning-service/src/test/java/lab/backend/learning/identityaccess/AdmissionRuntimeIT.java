package lab.backend.learning.identityaccess;

import static org.assertj.core.api.Assertions.*;
import static lab.backend.learning.identityaccess.AdmissionWire.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;
import lab.backend.learning.transport.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.*;

/** Packaged Learning + actual own PostgreSQL/mTLS. INT-01 is a controlled
 * provider-contract fixture; canonical Identity remains owned by R04d. */
@TestMethodOrder(MethodOrderer.MethodName.class) @Timeout(60)
class AdmissionRuntimeIT {
  static Properties lab=new Properties(),options=new Properties();
  static Path trustFile,config,root,artifact,log;
  static TransportSettings settings;
  static PGSimpleDataSource ds,migrator;
  static AdmissionRuntime runtime;
  static LearningAccess access;
  static Process process;
  static HttpsServer provider;
  static ExecutorService providerThreads;
  static UUID realm,generation,user=UUID.randomUUID();
  static volatile String mode="ALLOW";
  static AtomicInteger requests=new AtomicInteger();

  @BeforeAll static void setup()throws Exception{
    String l=System.getProperty("lab.config"),t=System.getProperty("trust.config");
    if(l==null||t==null||l.startsWith("${")||t.startsWith("${"))throw new IllegalStateException("Actual owned DB/TLS required");
    try(var reader=Files.newBufferedReader(Path.of(l))){lab.load(reader);}trustFile=Path.of(t).toRealPath();
    root=Path.of(WireCodec.JSON.readTree(Files.readAllBytes(trustFile.getParent().resolve("ownership.json"))).get("workspace").asString()).toRealPath();
    ds=source("app");migrator=source("migrator");
    Flyway.configure().dataSource(migrator).schemas("migration_metadata").defaultSchema("migration_metadata").createSchemas(false).cleanDisabled(true).locations("classpath:db/migration","classpath:db-test-migration").load().migrate();
    settings=TransportSettings.load(environment());realm=UUID.fromString(settings.realm());generation=UUID.fromString(settings.generation());provision(realm,generation);provision(realm,generation);
    var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(settings.store(settings.directory().resolve("identity.p12")),settings.storePassword().toCharArray());
    var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(settings.store(settings.trustStore()));var tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
    provider=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);provider.setHttpsConfigurator(new HttpsConfigurator(tls){@Override public void configure(HttpsParameters p){SSLParameters s=tls.getDefaultSSLParameters();s.setNeedClientAuth(true);p.setSSLParameters(s);}});
    providerThreads=Executors.newCachedThreadPool();provider.setExecutor(providerThreads);provider.createContext("/internal/v1/auth-context/admissions",AdmissionRuntimeIT::provider);provider.start();
    options.putAll(Map.of("schemaVersion","1","enabled","true","realm",realm.toString(),"generation",generation.toString(),"topologyVersion",TOPOLOGY,"dbRunId",lab.getProperty("lab.runId"),"identityPort",Integer.toString(provider.getAddress().getPort()),"origin","https://127.0.0.1"));
    config=settings.directory().resolve("admission.properties");store(config,options);runtime=AdmissionRuntime.load(environment(),settings);access=runtime.access();
    artifact=Path.of("target/learning-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();assertThat(artifact).isRegularFile();log=settings.directory().resolve("admission-runtime.log");start(config);
  }
  @AfterAll static void cleanup()throws Exception{
    stop();if(access!=null)access.close();if(provider!=null)provider.stop(0);if(providerThreads!=null){providerThreads.shutdownNow();assertThat(providerThreads.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    for(int port:new int[]{8080,8443})closed(port);
  }
  static PGSimpleDataSource source(String kind){var s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(lab.getProperty("lab.port"))});s.setDatabaseName("learning_db");s.setUser(lab.getProperty("learning."+kind+".user"));s.setPassword(lab.getProperty("learning."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(8);return s;}
  static StandardEnvironment environment(){var env=new StandardEnvironment();var p=new HashMap<String,Object>();p.put("backendlab.trust.config",trustFile.toString());if(config!=null)p.put("backendlab.admission.config",config.toString());env.getPropertySources().addFirst(new MapPropertySource("fixture",p));return env;}
  static void provision(UUID r,UUID g)throws Exception{try(var c=migrator.getConnection();var s=c.prepareStatement("SELECT set_config('backend_lab.provision_realm',?,false),set_config('backend_lab.provision_generation',?,false),set_config('backend_lab.provision_run_id',?,false)")){s.setString(1,r.toString());s.setString(2,g.toString());s.setString(3,lab.getProperty("lab.runId"));s.execute();try(var script=c.createStatement()){script.execute(Files.readString(root.resolve("ops/learning/provision-admission-realm.sql")));}}}
  static void store(Path file,Properties p)throws Exception{try(var writer=Files.newBufferedWriter(file)){p.store(writer,"owned lab config");}}

  static void provider(HttpExchange exchange)throws java.io.IOException{
    try{
      var cert=(java.security.cert.X509Certificate)((HttpsExchange)exchange).getSSLSession().getPeerCertificates()[0];
      if(!CertificateIdentity.identity(cert,"lab",true).equals("learning")||!exchange.getRequestMethod().equals("POST")||exchange.getRequestHeaders().containsKey("Cookie"))throw new IllegalStateException();
      var n=WireCodec.strict(exchange.getRequestBody().readNBytes(8193));requests.incrementAndGet();
      if(mode.equals("UNAVAILABLE")){exchange.sendResponseHeaders(503,-1);return;}if(mode.equals("REDIRECT")){exchange.getResponseHeaders().set("Location","https://127.0.0.1:1/leak");exchange.sendResponseHeaders(302,-1);return;}if(mode.equals("SLOW"))Thread.sleep(2500);
      byte[] body="{\"revision\":1}".getBytes();var r=new AdmissionWire.Request(n.get("sessionCredential").asString(),UUID.fromString(n.get("expectedContextId").asString()),UUID.fromString(n.get("canonicalPath").asString().substring("/api/v1/drafts/".length())),n.get("csrfToken").asString(),n.get("origin").asString(),body);
      var response=AdmissionWireTests.allow(r,realm);response.put("requestNonce",n.get("requestNonce").asString());response.put("userId",user.toString());
      assertThat(n.get("method").asString()).isEqualTo("PUT");assertThat(n.get("canonicalPath").asString()).isEqualTo(r.path);assertThat(n.get("query").asString()).isEmpty();assertThat(n.get("bodySha256").asString()).isEqualTo(sha(body));assertThat(n.get("requestBinding").asString()).isEqualTo(r.binding);assertThat(n.get("origin").asString()).isEqualTo("https://127.0.0.1");assertThat(n.get("csrfToken").asString()).isEqualTo("c".repeat(43));assertThat(n.get("action").asString()).isEqualTo(ACTION);assertThat(n.get("audience").asString()).isEqualTo("learning");
      switch(mode){case "NONCE"->response.put("requestNonce",UUID.randomUUID().toString());case "AUDIENCE"->response.put("audience","content");case "CONTEXT"->response.put("sessionContextId",UUID.randomUUID().toString());case "CSRF"->response.put("csrfVerified",false);case "CLOCK"->response.put("serverTime",AdmissionWireTests.FORMAT.format(Instant.now().plusSeconds(2)));case "EXPIRED"->response.put("validUntil",AdmissionWireTests.FORMAT.format(Instant.now()));default->{}}
      if(mode.equals("CLOCK_PAST")){Instant shifted=Instant.now().minusSeconds(2);response.put("issuedAt",AdmissionWireTests.FORMAT.format(shifted));response.put("serverTime",AdmissionWireTests.FORMAT.format(shifted));response.put("validUntil",AdmissionWireTests.FORMAT.format(shifted.plusSeconds(15)));}
      byte[] bytes=mode.equals("OVERSIZE")?(" ".repeat(65537)).getBytes():WireCodec.JSON.writeValueAsBytes(response);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
    }catch(Exception|AssertionError denied){try{exchange.sendResponseHeaders(500,-1);}catch(Exception ignored){}}finally{exchange.close();}
  }
  static void start(Path conf)throws Exception{
    for(int port:new int[]{8080,8443}){closed(port);try(var socket=new ServerSocket()){socket.setReuseAddress(true);socket.bind(new InetSocketAddress("127.0.0.1",port));}}
    var b=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-jar",artifact.toString(),"--spring.profiles.active=admission","--backendlab.trust.config="+trustFile,"--backendlab.admission.config="+conf,"--logging.level.root=ERROR");
    b.directory(settings.directory().toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));b.environment().keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("SERVER_")||k.startsWith("MANAGEMENT_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("_JAVA_OPTIONS"));process=b.start();
    long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();while(System.nanoTime()<deadline){if(!process.isAlive())throw new IllegalStateException("Owned receiver exited; inspect retained runtime log");try{if(call("edge",8080,"/api/v1/transport/status","GET",null,Map.of()).statusCode()==200)return;}catch(java.io.IOException expected){}Thread.sleep(100);}throw new IllegalStateException("Receiver startup timeout");
  }
  static void stop()throws Exception{if(process!=null){process.destroy();if(!process.waitFor(8,TimeUnit.SECONDS)){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}process=null;}}
  static void closed(int port)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();int refused=0;while(System.nanoTime()<end){try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),150);refused=0;}catch(java.io.IOException e){if(++refused==2)return;}Thread.sleep(50);}throw new AssertionError("Owned listener still reachable: "+port);}
  static HttpResponse<byte[]> call(String caller,int port,String path,String method,byte[] body,Map<String,String> headers)throws Exception{
    try(var client=HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,settings.directory().resolve(caller+".p12"),"learning")).connectTimeout(Duration.ofSeconds(1)).build()){
      var q=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));if(body!=null)q.header("Content-Type","application/json");headers.forEach(q::header);return client.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
  }
  static void denied(HttpResponse<byte[]> response,int status){assertThat(response.statusCode()).isEqualTo(status);assertThat(response.headers().allValues("Set-Cookie")).isEmpty();assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");assertThat(WireCodec.strict(response.body()).get("code").asString()).isNotEmpty();assertThat(new String(response.body())).doesNotContain("a".repeat(43),"SQLException","stackTrace");}
  static int receiptCount(UUID operation)throws SQLException{try(var c=ds.getConnection();var s=c.prepareStatement("SELECT count(*) FROM learning.auth_command_receipt WHERE operation_id=?")){s.setObject(1,operation);try(var r=s.executeQuery()){r.next();return r.getInt(1);}}}

  @Test @Tag("SEC-01") void aaMutationUsesFreshAdmissionAndCommitsEffectAtomically(){
    mode="ALLOW";int previous=requests.get();String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
    assertThat(access.<String>mutate(AdmissionGateIT.request(),(c,u)->{try(var s=c.prepareStatement("INSERT INTO learning.transaction_probe VALUES (?,0,'runtime')")){s.setString(1,first);s.executeUpdate();}return first;})).isEqualTo(first);
    assertThat(access.<String>mutate(AdmissionGateIT.request(),(c,u)->{try(var s=c.prepareStatement("INSERT INTO learning.transaction_probe VALUES (?,0,'runtime')")){s.setString(1,second);s.executeUpdate();}return second;})).isEqualTo(second);assertThat(requests.get()).isEqualTo(previous+2);
    try(var c=ds.getConnection();var s=c.prepareStatement("SELECT count(*) FROM learning.transaction_probe WHERE id IN (?,?) AND payload='runtime'")){s.setString(1,first);s.setString(2,second);try(var rows=s.executeQuery()){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}}catch(SQLException e){throw new AssertionError(e);}
  }
  @Test @Tag("SEC-05") void abUnavailableProviderDoesNotUseCachedAllow(){mode="UNAVAILABLE";try{assertThatThrownBy(()->access.mutate(AdmissionGateIT.request(),(c,u)->{throw new AssertionError("cached allow used");})).isInstanceOf(WireCodec.Denied.class);}finally{mode="ALLOW";}}
  @ParameterizedTest @ValueSource(strings={"NONCE","AUDIENCE","CONTEXT","CSRF","CLOCK","CLOCK_PAST","EXPIRED","REDIRECT","OVERSIZE","SLOW"}) @Tag("SEC-05")
  void acWrongProviderResponseAndBoundedFailuresDenyBeforeEffect(String variant){mode=variant;long before=System.nanoTime();try{assertThatThrownBy(()->access.mutate(AdmissionGateIT.request(),(c,u)->{throw new AssertionError("bad admission used");})).isInstanceOf(WireCodec.Denied.class);assertThat(Duration.ofNanos(System.nanoTime()-before)).isLessThan(Duration.ofSeconds(5));}finally{mode="ALLOW";}}
  @ParameterizedTest @ValueSource(strings={"content","learning","operator"}) @Tag("SEC-15")
  void adOnlyIdentityCertificateMayFence(String caller)throws Exception{var op=UUID.randomUUID();denied(call(caller,8443,FENCE_PATH+op,"PUT",AdmissionGateIT.fenceBody(op,UUID.randomUUID(),null,null,3,realm,generation),Map.of()),403);}
  @Test @Tag("SEC-15") void aePublicAndBrowserHeadersCannotReachFence()throws Exception{var op=UUID.randomUUID();byte[] body=AdmissionGateIT.fenceBody(op,UUID.randomUUID(),null,null,1,realm,generation);denied(call("edge",8080,FENCE_PATH+op,"PUT",body,Map.of()),404);denied(call("edge",8443,FENCE_PATH+op,"PUT",body,Map.of()),403);for(String header:List.of("Origin","Cookie","X-User-Id","X-Audience","X-Authz-Context"))denied(call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of(header,"a".repeat(43))),403);}
  @Test @Tag("SEC-09") void afDurableDuplicateAndConflictingEnvelope()throws Exception{var op=UUID.randomUUID();var u=UUID.randomUUID();byte[] body=AdmissionGateIT.fenceBody(op,u,null,null,5,realm,generation);var first=call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of());assertThat(first.statusCode()).isEqualTo(200);assertThat(WireCodec.strict(first.body()).get("appliedFloor").asString()).isEqualTo("5");assertThat(call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of()).body()).isEqualTo(first.body());denied(call("identity",8443,FENCE_PATH+op,"PUT",AdmissionGateIT.fenceBody(op,u,null,null,6,realm,generation),Map.of()),409);}
  @Test @Tag("SEC-02") void agAbsentSessionTombstoneBlocksActualOnlineMutation()throws Exception{var req=AdmissionGateIT.request();var op=UUID.randomUUID();byte[] body=AdmissionGateIT.fenceBody(op,user,UUID.randomUUID(),req.context,1,realm,generation);assertThat(call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of()).statusCode()).isEqualTo(200);AdmissionGateIT.denied(()->access.mutate(req,(c,u)->u),"SESSION_FENCED");}
  @Test @Tag("SEC-15") void ahProvisionAndRuntimeMismatchCannotResetSecurityState()throws Exception{assertThatThrownBy(()->provision(realm,UUID.randomUUID())).isInstanceOf(SQLException.class);var original=options.getProperty("generation");options.setProperty("generation",UUID.randomUUID().toString());store(config,options);try{assertThatThrownBy(()->AdmissionRuntime.load(environment(),settings)).isInstanceOf(IllegalStateException.class);}finally{options.setProperty("generation",original);store(config,options);}}
  @Test @Tag("SEC-15") void aiSchemaDigestGenerationMethodAndBodyLimitsFailClosed()throws Exception{
    var op=UUID.randomUUID();byte[] body=AdmissionGateIT.fenceBody(op,UUID.randomUUID(),null,null,1,realm,generation);
    denied(call("identity",8443,FENCE_PATH+op,"POST",body,Map.of()),405);
    denied(call("identity",8443,FENCE_PATH+op,"PUT","{}".getBytes(),Map.of()),400);
    denied(call("identity",8443,FENCE_PATH+op,"PUT",new byte[8193],Map.of()),413);
    denied(call("identity",8443,FENCE_PATH+op,"PUT",AdmissionGateIT.fenceBody(op,UUID.randomUUID(),null,null,1,realm,UUID.randomUUID()),Map.of()),409);
    byte[] changed=new String(body).replace("\"epochFloor\":\"1\"","\"epochFloor\":\"2\"").getBytes();
    denied(call("identity",8443,FENCE_PATH+op,"PUT",changed,Map.of()),403);
    assertThat(receiptCount(op)).isZero();
  }
  @Test @Tag("SEC-15") void ajStalledUploadHasBoundedIdleTimeoutAndNoFenceEffect()throws Exception{
    var op=UUID.randomUUID();long before=System.nanoTime();
    try(var socket=(SSLSocket)TransportPeerClient.tls(settings,settings.directory().resolve("identity.p12"),"learning").getSocketFactory().createSocket("127.0.0.1",8443)){
      socket.setSoTimeout(8000);socket.startHandshake();
      String request="PUT "+FENCE_PATH+op+" HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\n{";
      socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();
      String response=new String(socket.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
      assertThat(response).contains("408","REQUEST_TIMEOUT","no-store").doesNotContain("Set-Cookie","<html","Exception");
    }
    assertThat(Duration.ofNanos(System.nanoTime()-before)).isLessThan(Duration.ofSeconds(7));
    assertThat(receiptCount(op)).isZero();
  }
  @Test @Tag("SEC-05") void akWrongOriginCannotCallProviderOrMutate(){
    int previous=requests.get();var r=new AdmissionWire.Request("a".repeat(43),UUID.randomUUID(),UUID.randomUUID(),"c".repeat(43),"https://wrong.example","{\"revision\":1}".getBytes());
    assertThatThrownBy(()->access.mutate(r,(c,u)->{throw new AssertionError("wrong origin mutation");})).isInstanceOf(lab.backend.learning.transport.WireCodec.Denied.class);
    assertThat(requests.get()).isEqualTo(previous);
  }
  @Test @Tag("SEC-09") void zzRestartAndLostAckPreserveReceiptAndDeny()throws Exception{var req=AdmissionGateIT.request();var op=UUID.randomUUID();byte[] body=AdmissionGateIT.fenceBody(op,user,UUID.randomUUID(),req.context,1,realm,generation);assertThat(call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of()).statusCode()).isEqualTo(200);stop();start(config);var again=call("identity",8443,FENCE_PATH+op,"PUT",body,Map.of());assertThat(again.statusCode()).isEqualTo(200);assertThat(WireCodec.strict(again.body()).get("operationId").asString()).isEqualTo(op.toString());AdmissionGateIT.denied(()->access.mutate(req,(c,u)->u),"SESSION_FENCED");}
}
