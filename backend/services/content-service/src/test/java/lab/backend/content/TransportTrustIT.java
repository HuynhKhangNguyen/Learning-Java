package lab.backend.content;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.ConfigurableApplicationContext;
import lab.backend.content.transport.*;

@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(30)
class TransportTrustIT {
  private static final String SVC="content";
  private static ConfigurableApplicationContext context;
  private static TransportSettings settings;
  private static Path config;
  private static Process artifactProcess;
  private static Path artifactLog;
  private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  @BeforeAll @Timeout(90) static void startRealTlsReceiver()throws Exception{
    String input=System.getProperty("trust.config");
    if(input==null || input.isBlank() || input.startsWith("${"))throw new IllegalStateException("Required trust config missing; no skipped suite PASS");
    config=Path.of(input).toRealPath();
    for(int port:new int[]{8080,8443})try(ServerSocket probe=new ServerSocket()){probe.bind(new java.net.InetSocketAddress("127.0.0.1",port));}
    var app=ContentApplication.application();app.setRegisterShutdownHook(false);app.setLogStartupInfo(false);app.setDefaultProperties(Map.of("logging.level.root","ERROR"));
    if(Boolean.getBoolean("trust.artifact")){
      var environment=new org.springframework.core.env.StandardEnvironment();
      environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("fixture",Map.of("backendlab.trust.config",config.toString())));
      settings=TransportSettings.load(environment);
      Path artifact=Path.of("target",SVC+"-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
      assertThat(artifact).isRegularFile();
      Path cwd=Files.createTempDirectory(settings.directory(),"artifact-");
      artifactLog=cwd.resolve("runtime.log");
      var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-jar",artifact.toString(),"--spring.profiles.active=transport","--backendlab.trust.config="+config,"--logging.level.root=ERROR");
      command.directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(artifactLog.toFile());
      command.environment().keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("SPRING_") || k.toUpperCase(Locale.ROOT).startsWith("SERVER_") || k.toUpperCase(Locale.ROOT).startsWith("MANAGEMENT_") || k.equals("JAVA_TOOL_OPTIONS") || k.equals("_JAVA_OPTIONS"));
      artifactProcess=command.start();
      long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();boolean ready=false;
      while(System.nanoTime()<deadline){
        if(!artifactProcess.isAlive())throw new IllegalStateException("Packaged transport exited; inspect owned runtime.log");
        try{if(new TransportTrustIT().call("edge",8080,"/api/v1/transport/status","GET",null,Map.of()).statusCode()==200){ready=true;break;}}catch(java.io.IOException expectedStartup){}
        Thread.sleep(100);
      }
      assertThat(ready).as("packaged TLS startup").isTrue();
      System.out.println("ARTIFACT_TRANSPORT "+SVC+" sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)))+" cwd="+cwd+" pid="+artifactProcess.pid());
    }else{
      context=app.run("--spring.profiles.active=transport","--backendlab.trust.config="+config);
      settings=context.getBean(TransportSettings.class);
    }
  }
  @AfterAll static void stopOnlyOwnedContext()throws Exception{
    closeReceiver();
    for(int port:new int[]{8080,8443})try(Socket probe=new Socket()){assertThatThrownBy(()->probe.connect(new java.net.InetSocketAddress("127.0.0.1",port),1000)).isInstanceOf(java.io.IOException.class);}
  }
  private static void closeReceiver()throws Exception{
    if(context!=null){context.close();context=null;}
    if(artifactProcess!=null){
      artifactProcess.destroy();
      if(!artifactProcess.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){artifactProcess.destroyForcibly();assertThat(artifactProcess.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
      assertThat(artifactProcess.isAlive()).isFalse();artifactProcess=null;
    }
    // Process exit is not the Windows TCP close boundary. Allow the owned
    // listener to settle, but fail within five seconds if it stays reachable.
    for(int port:new int[]{8080,8443}) {
      long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();int refused=0;
      while(System.nanoTime()<deadline && refused<2){
        try(Socket probe=new Socket()){probe.connect(new java.net.InetSocketAddress("127.0.0.1",port),200);refused=0;}
        catch(java.net.ConnectException closed){refused++;}
        Thread.sleep(50);
      }
      assertThat(refused).as("owned TCP listener closed after process/context exit").isEqualTo(2);
    }
  }
  private HttpResponse<byte[]> call(String identity,int port,String path,String method,byte[] body,Map<String,String> headers)throws Exception{
    Path store=identity==null?null:settings.directory().resolve(identity+".p12");
    try(HttpClient client=HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,store,SVC)).connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build()){
      var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5));
      if(body!=null)request.header("Content-Type","application/json");headers.forEach(request::header);
      return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
  }
  private static String sender(){return SVC.equals("identity")?"content":"identity";}
  private static String action(){return SVC.equals("identity")?"ADMISSION_TRANSPORT":"FENCE_TRANSPORT";}
  private byte[] body(String action,String audience,String revision,Instant issued,Instant until){
    String nonce=UUID.randomUUID().toString();
    String canonical="POST\n/internal/v1/transport/probes\n"+action+"\n"+audience+"\n"+nonce+"\n"+revision+"\n"+issued+"\n"+until;
    try{return WireCodec.JSON.writeValueAsBytes(Map.of("schemaVersion",1,"action",action,"audience",audience,"nonce",nonce,"expectedRevision",revision,"issuedAt",FORMAT.format(issued),"validUntil",FORMAT.format(until),"requestBinding",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)))));}catch(Exception e){throw new IllegalStateException(e);}
  }
  private byte[] good(){Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);return body(action(),SVC,"9007199254740993",now,now.plusSeconds(15));}
  private void denied(HttpResponse<byte[]> r,int status){assertThat(r.statusCode()).isEqualTo(status);assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");var json=WireCodec.strict(r.body());assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);UUID.fromString(json.get("traceId").asString());assertThat(new String(r.body(),StandardCharsets.UTF_8)).doesNotContain("do-not-echo-sensitive-marker","password","stackTrace","SQLException");assertThat(r.headers().allValues("Set-Cookie")).isEmpty();}
  @Test @Tag("SEC-14") void aaPublicEdgeCertificateGetsFoundationStatus()throws Exception{
    var r=call("edge",8080,"/api/v1/transport/status","GET",null,Map.of());assertThat(r.statusCode()).isEqualTo(200);var n=WireCodec.strict(r.body());assertThat(n.get("businessReady").asBoolean()).isFalse();assertThat(n.get("clockVerified").asBoolean()).isTrue();assertThat(n.get("service").asString()).isEqualTo(SVC);assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
  }
  @ParameterizedTest @ValueSource(strings={"identity","content","learning","operator"}) @Tag("SEC-14")
  void publicListenerRejectsAllNonEdgeIdentities(String caller)throws Exception{denied(call(caller,8080,"/api/v1/transport/status","GET",null,Map.of()),403);}
  @Test @Tag("SEC-14") void privateListenerRejectsEdgeIdentity()throws Exception{denied(call("edge",8443,"/internal/v1/transport/probes","POST",good(),Map.of()),403);}
  @ParameterizedTest @ValueSource(strings={"wrong-ca","wrong-eku","expired"}) @Tag("SEC-14")
  void invalidTlsClientMaterialCannotReachEitherListener(String variant){for(int port:new int[]{8080,8443})assertThatThrownBy(()->call(variant,port,"/api/v1/transport/status","GET",null,Map.of())).isInstanceOf(java.io.IOException.class);}
  @Test @Tag("SEC-20") void cookieWithoutClientCertificateDoesNotAuthenticate(){for(int port:new int[]{8080,8443})assertThatThrownBy(()->call(null,port,"/internal/v1/transport/probes","POST",good(),Map.of("Cookie","session=do-not-echo-sensitive-marker"))).isInstanceOf(java.io.IOException.class);}
  @ParameterizedTest @ValueSource(strings={"wrong-san","wrong-env","ambiguous"}) @Tag("SEC-14")
  void trustedIssuerDoesNotReplaceSanEnvironmentAndIdentity(String variant)throws Exception{denied(call(variant,8443,"/internal/v1/transport/probes","POST",good(),Map.of()),403);}
  @ParameterizedTest @ValueSource(strings={"/internal/v1/transport/probes","/internal/v1/auth-context/admissions","/actuator/health","/bootstrap/status"}) @Tag("SEC-14")
  void publicListenerDoesNotExposePrivateOrBootstrapRoutes(String path)throws Exception{denied(call("edge",8080,path,"GET",null,Map.of()),404);}
  @Test @Tag("SEC-14") void privateListenerDoesNotExposePublicRoutes()throws Exception{denied(call("operator",8443,"/api/v1/transport/status","GET",null,Map.of()),404);}
  @Test @Tag("SEC-15") void authorizedPeerActionHasNoUserAdmissionOrBusinessReadiness()throws Exception{
    var r=call(sender(),8443,"/internal/v1/transport/probes","POST",good(),Map.of());assertThat(r.statusCode()).isEqualTo(200);var n=WireCodec.strict(r.body());assertThat(n.get("caller").asString()).isEqualTo(sender());assertThat(n.get("businessReady").asBoolean()).isFalse();assertThat(n.has("userId")).isFalse();assertThat(n.get("expectedRevision").asString()).isEqualTo("9007199254740993");
  }
  @Test @Tag("SEC-15") void operatorHasOnlyExplicitOperatorAction()throws Exception{
    Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);assertThat(call("operator",8443,"/internal/v1/transport/probes","POST",body("OPS_TRANSPORT",SVC,"0",now,now.plusSeconds(15)),Map.of()).statusCode()).isEqualTo(200);
    denied(call("operator",8443,"/internal/v1/transport/probes","POST",good(),Map.of()),403);
  }
  @Test @Tag("SEC-15") void wrongActionAndAudienceAreDeniedDespiteValidCertificate()throws Exception{
    Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);denied(call("content",8443,"/internal/v1/transport/probes","POST",body("FENCE_TRANSPORT",SVC,"0",now,now.plusSeconds(15)),Map.of()),403);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",body(action(),"wrong", "0",now,now.plusSeconds(15)),Map.of()),403);
  }
  @ParameterizedTest @ValueSource(strings={"X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto"}) @Tag("SEC-15")
  void forgedHeadersDoNotProvideIdentityOrBinding(String header)throws Exception{denied(call(sender(),8443,"/internal/v1/transport/probes","POST",good(),Map.of(header,"do-not-echo-sensitive-marker")),403);}
  @Test @Tag("SEC-20") void originCorsPreflightAndCookieAreNotServiceAuth()throws Exception{
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",good(),Map.of("Origin","https://attacker.invalid")),403);
    denied(call(sender(),8443,"/internal/v1/transport/probes","OPTIONS",null,Map.of("Origin","https://attacker.invalid")),403);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",good(),Map.of("Cookie","session=do-not-echo-sensitive-marker")),403);
  }
  @Test @Tag("SEC-15") void nearCutoffAndFutureClockClaimsFailClosed()throws Exception{
    Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);denied(call(sender(),8443,"/internal/v1/transport/probes","POST",body(action(),SVC,"0",now,now.plusSeconds(6)),Map.of()),403);
    Instant future=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusSeconds(60);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",body(action(),SVC,"0",future,future.plusSeconds(15)),Map.of()),403);
  }
  @Test @Tag("SEC-15") void actualPeerAdapterChecksReceiverIdentityAndReturnsBoundMetadata()throws Exception{
    var foreign=new TransportSettings(settings.directory(),settings.directory().resolve(sender()+".p12"),settings.trustStore(),settings.storePassword(),settings.dbPort(),settings.dbPassword(),settings.dbRunId(),settings.realm(),settings.generation(),0);
    try(var peer=new TransportPeerClient(foreign,SVC)){var n=WireCodec.strict(peer.probe(good()));assertThat(n.get("caller").asString()).isEqualTo(sender());assertThat(n.get("businessReady").asBoolean()).isFalse();}
    try(var peer=new TransportPeerClient(foreign,SVC.equals("identity")?"content":"identity")){assertThatThrownBy(()->peer.probe(good())).isInstanceOf(Exception.class);}
  }
  @Test @Tag("SEC-14") void hostnameVerificationRemainsEnabled()throws Exception{
    try(Socket tcp=new Socket("127.0.0.1",8443);SSLSocket tls=(SSLSocket)TransportPeerClient.tls(settings,settings.directory().resolve(sender()+".p12"),SVC).getSocketFactory().createSocket(tcp,"wrong.backend-lab.test",8443,true)){
      SSLParameters p=tls.getSSLParameters();p.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(p);assertThatThrownBy(tls::startHandshake).isInstanceOf(SSLException.class);
    }
  }
  @ParameterizedTest @ValueSource(strings={"/internal/v1/transport/probes?owner=a&owner=b","/internal/v1/transport/probes;role=admin","/internal/v1/transport//probes","/internal/v1/transport/%70robes"}) @Tag("SEC-15")
  void ambiguousPathAndDuplicateQueryRejectedBeforeProbe(String path)throws Exception{denied(call(sender(),8443,path,"POST",good(),Map.of()),400);}
  @Test @Tag("SEC-15") void wrongBindingUnknownFieldsAndOversizedBodyAreRejected()throws Exception{
    String text=new String(good(),StandardCharsets.UTF_8);var tree=WireCodec.JSON.readTree(text);String original=tree.get("requestBinding").asString();String tampered=(original.charAt(0)=='0'?"1":"0")+original.substring(1);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",text.replace(original,tampered).getBytes(StandardCharsets.UTF_8),Map.of()),403);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",text.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"owner\":\"do-not-echo-sensitive-marker\"").getBytes(StandardCharsets.UTF_8),Map.of()),400);
    denied(call(sender(),8443,"/internal/v1/transport/probes","POST",new byte[65537],Map.of()),413);
  }
  @ParameterizedTest @ValueSource(strings={"environment","realm","generation","dbRunId","dbPassword","clock","certificate"}) @Tag("SEC-14")
  void badRuntimeConfigClockAndCertificateFailBeforeListener(String kind)throws Exception{
    Properties p=new Properties();try(var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){p.load(reader);}
    switch(kind){case "clock"->p.setProperty("test.clockOffsetMs","2000");case "certificate"->p.setProperty("keyStore","wrong-env.p12");case "dbRunId"->p.setProperty(kind,UUID.randomUUID().toString());case "dbPassword"->p.setProperty(kind,"0".repeat(48));default->p.setProperty(kind,"invalid");}
    Path negative=settings.directory().resolve("negative-"+UUID.randomUUID()+".properties");try(var writer=Files.newBufferedWriter(negative,StandardCharsets.UTF_8)){p.store(writer,"test-only negative config");}
    var app=ContentApplication.application();app.setRegisterShutdownHook(false);app.setLogStartupInfo(false);app.setDefaultProperties(Map.of("logging.level.root","OFF"));
    assertThatThrownBy(()->app.run("--spring.profiles.active=transport","--backendlab.trust.config="+negative)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Transport");
  }
  @Test @Tag("SEC-14") void missingConfigFailsClosed(){var app=ContentApplication.application();app.setRegisterShutdownHook(false);app.setDefaultProperties(Map.of("logging.level.root","OFF"));assertThatThrownBy(()->app.run("--spring.profiles.active=transport")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Transport config missing");}
  @Test @Tag("SEC-15") void zzPeerTransportRejectsRedirectRetryOversizeAndStalledBody()throws Exception{
    closeReceiver();
    var pool=java.util.concurrent.Executors.newCachedThreadPool();
    // The JDK HttpServer cannot enable reuse before bind; on Windows the owned
    // Tomcat connection's TIME_WAIT can reject that fixture, not the peer client.
    // Keep the actual mTLS/wire fault test on the fixed destination with an
    // explicitly reusable, bounded, owned test socket; never kill a foreign PID.
    try(Socket probe=new Socket()){assertThatThrownBy(()->probe.connect(new java.net.InetSocketAddress("127.0.0.1",8443),1000)).isInstanceOf(java.io.IOException.class);}
    var server=(SSLServerSocket)TransportPeerClient.tls(settings,settings.keyStore(),SVC).getServerSocketFactory().createServerSocket();
    server.setReuseAddress(true);server.setNeedClientAuth(true);
    server.bind(new java.net.InetSocketAddress("127.0.0.1",8443));
    var mode=new java.util.concurrent.atomic.AtomicReference<String>();var requests=new java.util.concurrent.atomic.AtomicInteger();var release=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);
    var fixtureErrors=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
    pool.submit(()->{
      try{while(!server.isClosed()){
        SSLSocket exchange=(SSLSocket)server.accept();
        pool.submit(()->{try(exchange){
          exchange.setSoTimeout(5000);exchange.startHandshake();
          var input=exchange.getInputStream();var headers=new java.io.ByteArrayOutputStream();
          int matched=0;byte[] end={'\r','\n','\r','\n'};
          while(matched<4){int b=input.read();if(b<0 || headers.size()>=16384)throw new java.io.IOException("Bounded fixture request rejected");headers.write(b);matched=b==end[matched]?matched+1:b=='\r'?1:0;}
          String text=headers.toString(StandardCharsets.US_ASCII);
          assertThat(text).startsWith("POST /internal/v1/transport/probes HTTP/1.1\r\n");
          var length=java.util.regex.Pattern.compile("(?im)^Content-Length: ([0-9]+)\\r?$",java.util.regex.Pattern.MULTILINE).matcher(text);
          assertThat(length.find()).isTrue();int size=Integer.parseInt(length.group(1));assertThat(size).isBetween(1,WireCodec.MAX_BYTES);
          assertThat(input.readNBytes(size)).hasSize(size);
          requests.incrementAndGet();String scenario=mode.get();var output=exchange.getOutputStream();
          String response=switch(scenario){
            case "redirect"->"302 Found\r\nLocation: https://127.0.0.1:1/forbidden\r\nContent-Length: 0";
            case "unavailable"->"503 Service Unavailable\r\nContent-Length: 0";
            case "oversize"->"200 OK\r\nContent-Length: 65537";
            default->"200 OK\r\nContent-Length: 10";};
          output.write(("HTTP/1.1 "+response+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          if(scenario.equals("oversize"))output.write(new byte[65537]);
          else if(scenario.equals("stall")){output.write('{');output.flush();entered.countDown();release.await(10,java.util.concurrent.TimeUnit.SECONDS);}
          output.flush();
        }catch(java.io.IOException expectedClientDisconnect){}catch(Throwable error){fixtureErrors.add(error);}});
      }}catch(java.io.IOException stopped){if(!server.isClosed())fixtureErrors.add(stopped);}
    });
    var foreign=new TransportSettings(settings.directory(),settings.directory().resolve(sender()+".p12"),settings.trustStore(),settings.storePassword(),settings.dbPort(),settings.dbPassword(),settings.dbRunId(),settings.realm(),settings.generation(),0);
    try{
      for(String scenario:new String[]{"redirect","unavailable","oversize","stall"}){
        mode.set(scenario);int before=requests.get();long start=System.nanoTime();
        try(var peer=new TransportPeerClient(foreign,SVC)){
          if(scenario.equals("redirect") || scenario.equals("unavailable"))assertThatThrownBy(()->peer.probe(good())).isInstanceOf(WireCodec.Denied.class).satisfies(e->assertThat(((WireCodec.Denied)e).status).isEqualTo(scenario.equals("redirect")?302:503));
          else assertThatThrownBy(()->peer.probe(good())).isInstanceOf(Exception.class);
        }
        assertThat(requests.get()-before).as("no application retry or redirect").isEqualTo(1);
        if(scenario.equals("stall")){assertThat(entered.getCount()).isZero();assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(java.time.Duration.ofSeconds(6));}
      }
      assertThat(fixtureErrors).isEmpty();
    }finally{release.countDown();server.close();pool.shutdownNow();assertThat(pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
  }
}
