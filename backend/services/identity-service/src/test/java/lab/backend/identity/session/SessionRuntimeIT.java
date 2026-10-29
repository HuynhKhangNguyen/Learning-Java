package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import java.io.Reader;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lab.backend.identity.IdentityApplication;
import lab.backend.identity.credential.*;
import lab.backend.identity.transport.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.*;
import tools.jackson.databind.JsonNode;

/** Packaged Identity, real PostgreSQL and mTLS. Browser proof is a separate real
 * Chromium fixture; no application UI/production-edge/distributed ACK claim. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(90)
public class SessionRuntimeIT {
  static Properties lab=new Properties(),trust=new Properties(),session=new Properties();
  static PGSimpleDataSource ds,migrator;
  static TransportSettings settings;
  static Path root,trustFile,sessionFile,artifact,runtimeLog;
  static Process process;
  static final String PASSWORD="  密碼🔐 correct password value  ";
  static final Map<String,String> SOURCE=Map.of("X-Backend-Lab-Client-IP","127.0.0.1");
  static CredentialWorkflow workflow;
  static PasswordCorpus corpus;
  record Login(String cookie,JsonNode view) {}
  static String origin;

  @BeforeAll static void setup() throws Exception {
    String config=System.getProperty("lab.config"),tls=System.getProperty("trust.config");
    if(config==null || tls==null || config.startsWith("${") || tls.startsWith("${"))throw new IllegalStateException("Actual owned DB and TLS configs required");
    try(Reader r=Files.newBufferedReader(Path.of(config),StandardCharsets.UTF_8)){lab.load(r);}
    trustFile=Path.of(tls).toRealPath();try(Reader r=Files.newBufferedReader(trustFile,StandardCharsets.UTF_8)){trust.load(r);}
    root=Path.of(WireCodec.JSON.readTree(Files.readAllBytes(trustFile.getParent().resolve("ownership.json"))).get("workspace").asString()).toRealPath();
    ds=source("app");migrator=source("migrator");
    Flyway.configure().dataSource(migrator).schemas("migration_metadata").defaultSchema("migration_metadata").locations("classpath:db/migration").load().migrate();
    provision(trust.getProperty("realm"));provision(trust.getProperty("realm")); // exact retry, not reset
    try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){origin="https://localhost:"+socket.getLocalPort();}
    Path corpusPath=root.resolve("ops/identity/password-corpus.txt");
    String hash=WireCodec.JSON.readTree(Files.readAllBytes(root.resolve("ops/identity/password-corpus.json"))).get("sha256").asString();
    corpus=PasswordCorpus.load(corpusPath,hash);workflow=new CredentialWorkflow(ds);
    byte[] hmac=new byte[32];new java.security.SecureRandom().nextBytes(hmac);
    session.putAll(Map.of("schemaVersion","1","enabled","true","realm",trust.getProperty("realm"),"origin",origin,
        "corpusPath",corpusPath.toString(),"corpusSha256",hash,"throttleKey",Base64.getEncoder().encodeToString(hmac),"dbRunId",lab.getProperty("lab.runId")));
    sessionFile=trustFile.getParent().resolve("session.properties");store(sessionFile,session);
    var env=environment(sessionFile);settings=TransportSettings.load(env);SessionRuntime.load(env,settings);
    artifact=Path.of("target/identity-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();assertThat(artifact).isRegularFile();
    runtimeLog=trustFile.getParent().resolve("session-runtime.log");start();
  }
  @AfterAll static void cleanup() throws Exception {
    stop();
    for(int port:new int[]{8080,8443})try(Socket probe=new Socket()){assertThatThrownBy(()->probe.connect(new InetSocketAddress("127.0.0.1",port),500)).isInstanceOf(java.io.IOException.class);}
  }
  static StandardEnvironment environment(Path config){var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("fixture",Map.of("backendlab.trust.config",trustFile.toString(),"backendlab.session.config",config.toString())));return env;}
  static PGSimpleDataSource source(String kind){var s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(lab.getProperty("lab.port"))});s.setDatabaseName("identity_db");s.setUser(lab.getProperty("identity."+kind+".user"));s.setPassword(lab.getProperty("identity."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(6);return s;}
  static void provision(String realm) throws Exception {
    try(var c=migrator.getConnection();var s=c.prepareStatement("SELECT set_config('backend_lab.provision_realm',?,false),set_config('backend_lab.provision_run_id',?,false)")) {
      s.setString(1,realm);s.setString(2,lab.getProperty("lab.runId"));s.execute();
      try(var statement=c.createStatement()){statement.execute(Files.readString(root.resolve("ops/identity/provision-session-realm.sql")));}
    }
  }
  static void store(Path p,Properties values) throws Exception {try(var w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){values.store(w,"owned lab fixture; secrets never console output");}}
  static void start() throws Exception {
    // A closed listener may still have Windows TIME_WAIT sockets. Verify TCP
    // closure, then probe with reuse rather than confusing TIME_WAIT with a
    // surviving process. Never stop anything this test did not launch.
    for(int port:new int[]{8080,8443}) {
      long closedDeadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();boolean closed=false;
      while(System.nanoTime()<closedDeadline){try(Socket s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),200);}catch(java.io.IOException expectedClosed){closed=true;break;}Thread.sleep(50);}
      assertThat(closed).as("owned listener closed before restart").isTrue();
      try(ServerSocket s=new ServerSocket()){s.setReuseAddress(true);s.bind(new InetSocketAddress("127.0.0.1",port));}
    }
    var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-jar",artifact.toString(),"--spring.profiles.active=session","--backendlab.trust.config="+trustFile,"--backendlab.session.config="+sessionFile,"--logging.level.root=ERROR");
    builder.directory(trustFile.getParent().toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(runtimeLog.toFile()));
    builder.environment().keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("SPRING_") || k.toUpperCase(Locale.ROOT).startsWith("SERVER_") || k.toUpperCase(Locale.ROOT).startsWith("MANAGEMENT_") || k.equals("JAVA_TOOL_OPTIONS") || k.equals("_JAVA_OPTIONS"));
    process=builder.start();long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
    while(System.nanoTime()<deadline){if(!process.isAlive())throw new IllegalStateException("Session JAR exited; inspect owned session-runtime.log");try{if(call("GET","/api/v1/session",null,SOURCE).statusCode()==200)return;}catch(java.io.IOException startup){}Thread.sleep(100);}
    throw new IllegalStateException("Session JAR startup timeout");
  }
  static void stop() throws Exception {
    if(process!=null){process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}assertThat(process.isAlive()).isFalse();process=null;}
    // Match actual TCP closure, not just the process handle's exit notification.
    for(int port:new int[]{8080,8443}){
      long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();int refused=0;
      while(System.nanoTime()<deadline && refused<2){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),200);refused=0;}catch(ConnectException closed){refused++;}Thread.sleep(50);}
      assertThat(refused).as("owned session listener closed after JAR exit").isEqualTo(2);
    }
  }
  static HttpResponse<byte[]> call(String method,String path,byte[] body,Map<String,String> headers) throws Exception {return call("edge",8080,method,path,body,headers);}
  static HttpResponse<byte[]> call(String caller,int port,String method,String path,byte[] body,Map<String,String> headers) throws Exception {
    try(var client=HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,caller==null?null:settings.directory().resolve(caller+".p12"),"identity")).connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build()) {
      var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));headers.forEach(request::header);
      return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
  }
  static Map<String,String> headers(String cookie,String csrf){var h=new HashMap<>(SOURCE);h.put("Origin",origin);h.put("Content-Type","application/json");if(cookie!=null)h.put("Cookie",SessionAuthority.COOKIE+"="+cookie);if(csrf!=null)h.put("X-CSRF-TOKEN",csrf);return h;}
  static byte[] bytes(Map<String,Object> body){return WireCodec.JSON.writeValueAsBytes(body);}
  static JsonNode json(HttpResponse<byte[]> r,int status){assertThat(r.statusCode()).isEqualTo(status);assertThat(r.headers().firstValue("Cache-Control")).hasValue("no-store");assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");return WireCodec.strict(r.body());}
  static void denied(HttpResponse<byte[]> r,int status){var n=json(r,status);assertThat(n.get("schemaVersion").asInt()).isEqualTo(1);UUID.fromString(n.get("traceId").asString());assertThat(r.headers().allValues("Set-Cookie")).isEmpty();assertThat(new String(r.body(),StandardCharsets.UTF_8)).doesNotContain(PASSWORD,"SQLException","stackTrace","throttleKey");}
  static String cookie(HttpResponse<byte[]> r){var cookies=r.headers().allValues("Set-Cookie");assertThat(cookies).hasSize(1);assertThat(cookies.getFirst()).contains("Path=/","Secure","HttpOnly","SameSite=Lax","Expires=").doesNotContain("Domain=","Max-Age=0");return cookies.getFirst().split(";",2)[0].substring((SessionAuthority.COOKIE+"=").length());}
  static Login flow() throws Exception {var h=headers(null,null);h.put("X-Auth-Flow","credential");var r=call("POST","/api/v1/session",bytes(Map.of("schemaVersion",1)),h);return new Login(cookie(r),json(r,200));}
  static String account() throws Exception {String name="runtime"+UUID.randomUUID().toString().replace("-","").substring(0,12);var token=workflow.invite(name);assertThat(workflow.consume(CredentialWorkflow.Purpose.ACTIVATE,token.rawToken(),PASSWORD,corpus)).isTrue();return name;}
  static Login login(String account) throws Exception {return login(account,flow());}
  static Login login(String account,Login flow) throws Exception {
    var r=call("POST","/api/v1/auth/login",bytes(Map.of("schemaVersion",1,"expectedContextId",flow.view.get("sessionContextId").asString(),"loginName",account,"password",PASSWORD)),headers(flow.cookie,flow.view.get("csrfToken").asString()));
    return new Login(cookie(r),json(r,200));
  }
  static String scalar(String sql) throws Exception {try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
  static void sql(PGSimpleDataSource source,String sql) throws Exception {try(var c=source.getConnection();var s=c.createStatement()){s.execute(sql);}}

  @Test void aaReadOnlyBootstrapDoesNotCreateFlowOrCookie() throws Exception {
    var r=call("GET","/api/v1/session",null,SOURCE);assertThat(json(r,200).get("state").asString()).isEqualTo("ANONYMOUS");assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
  }
  @Test void abLoginRotatesCookieAndPollDoesNotRenewIdle() throws Exception {
    Login f=flow(),a=login(account(),f);assertThat(a.cookie).isNotEqualTo(f.cookie);assertThat(a.view.get("sessionContextId").asString()).isNotEqualTo(f.view.get("sessionContextId").asString());
    String before=scalar("SELECT last_interactive_at FROM identity.auth_session WHERE context_id='"+a.view.get("sessionContextId").asString()+"'");
    var r=call("GET","/api/v1/session",null,headers(a.cookie,null));assertThat(json(r,200).get("state").asString()).isEqualTo("ACTIVE");assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(scalar("SELECT last_interactive_at FROM identity.auth_session WHERE context_id='"+a.view.get("sessionContextId").asString()+"'")).isEqualTo(before);
  }
  @Test void acLogoutRetryAndStatusAreBoundAndNeverClearNewCookie() throws Exception {
    Login a=login(account()),b=login(account());String context=a.view.get("sessionContextId").asString();
    byte[] body=bytes(Map.of("schemaVersion",1,"expectedContextId",context));var h=headers(a.cookie,a.view.get("csrfToken").asString());h.put("Idempotency-Key",UUID.randomUUID().toString());
    var r=call("POST","/api/v1/auth/logout",body,h);String op=json(r,202).get("operationId").asString();assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(json(call("POST","/api/v1/auth/logout",body,h),202).get("operationId").asString()).isEqualTo(op);
    var status=headers(a.cookie,null);status.put("X-Session-Context",context);assertThat(json(call("GET","/api/v1/auth/operations/"+op,null,status),200).get("state").asString()).isEqualTo("PENDING_FENCES");
    status.put("Cookie",SessionAuthority.COOKIE+"="+b.cookie);denied(call("GET","/api/v1/auth/operations/"+op,null,status),404);
    h.put("Cookie",SessionAuthority.COOKIE+"="+b.cookie);denied(call("POST","/api/v1/auth/logout",body,h),409);
    assertThat(json(call("GET","/api/v1/session",null,headers(b.cookie,null)),200).get("state").asString()).isEqualTo("ACTIVE");
  }
  @ParameterizedTest @ValueSource(strings={"missing","null","https://attacker.invalid"}) void adWrongOriginCannotMintFlow(String value) throws Exception {
    var h=headers(null,null);h.put("X-Auth-Flow","credential");if(value.equals("missing"))h.remove("Origin");else h.put("Origin",value);
    denied(call("POST","/api/v1/session",bytes(Map.of("schemaVersion",1)),h),403);
  }
  @ParameterizedTest @ValueSource(strings={"X-User-Id","X-Owner-Id","X-Forwarded-For","X-Forwarded-Proto"}) void aeSpoofedHeadersRemainDenied(String name) throws Exception {var h=new HashMap<>(SOURCE);h.put(name,"spoof");denied(call("GET","/api/v1/session",null,h),403);}
  @ParameterizedTest @ValueSource(strings={"missing","hostname.invalid","127.0.0.1, 10.0.0.1","127.1","123","127.000.0.1","999.0.0.1"}) void afTrustedSourceMustBeOneLiteral(String ip) throws Exception {var h=new HashMap<String,String>();if(!ip.equals("missing"))h.put("X-Backend-Lab-Client-IP",ip);denied(call("GET","/api/v1/session",null,h),403);}
  @Test void agPrivateListenerAndNonEdgeCannotUseCookieForAuth() throws Exception {
    denied(call("content",8080,"GET","/api/v1/session",null,SOURCE),403);
    denied(call("operator",8443,"GET","/api/v1/session",null,headers(flow().cookie,null)),403);
    assertThatThrownBy(()->call(null,8080,"GET","/api/v1/session",null,SOURCE)).isInstanceOf(java.io.IOException.class);
  }
  @ParameterizedTest @ValueSource(strings={"wrongType","unknownField","duplicateField","schema2","oversize","malformedUtf8"}) void ahSchemaAndBodyNegatives(String variant) throws Exception {
    var h=headers(null,null);h.put("X-Auth-Flow","credential");byte[] body=bytes(Map.of("schemaVersion",1));int status=400;
    switch(variant){case "wrongType"->h.put("Content-Type","text/plain");case "unknownField"->body=bytes(Map.of("schemaVersion",1,"owner","fake"));case "schema2"->body=bytes(Map.of("schemaVersion",2));case "duplicateField"->body="{\"schemaVersion\":1,\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);case "oversize"->{body=new byte[4097];status=413;}case "malformedUtf8"->body=new byte[]{(byte)0xc0,(byte)0xaf};}
    denied(call("POST","/api/v1/session",body,h),status);
  }
  @Test void aiCsrfAndContextDenyBeforeCredentialBuckets() throws Exception {
    Login f=flow();String before=scalar("SELECT count(*) FROM identity.auth_throttle");
    byte[] body=bytes(Map.of("schemaVersion",1,"expectedContextId",f.view.get("sessionContextId").asString(),"loginName","unlisted","password",PASSWORD));
    denied(call("POST","/api/v1/auth/login",body,headers(f.cookie,"x".repeat(43))),403);
    assertThat(scalar("SELECT count(*) FROM identity.auth_throttle")).isEqualTo(before);
  }
  @Test void aiSlowUploadHasBoundedTimeoutBeforeAnyFlowEffect() throws Exception {
    String before=scalar("SELECT count(*) FROM identity.auth_preauth");
    try(var socket=(javax.net.ssl.SSLSocket)TransportPeerClient.tls(settings,settings.directory().resolve("edge.p12"),"identity").getSocketFactory().createSocket("127.0.0.1",8080)) {
      socket.setSoTimeout(8000);socket.startHandshake();
      String request="POST /api/v1/session HTTP/1.1\r\nHost: localhost\r\nOrigin: "+origin+"\r\nX-Backend-Lab-Client-IP: 127.0.0.1\r\nX-Auth-Flow: credential\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\n{";
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
      String response=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
      assertThat(response).contains("408","REQUEST_TIMEOUT","no-store").doesNotContain("Set-Cookie");
    }
    assertThat(scalar("SELECT count(*) FROM identity.auth_preauth")).isEqualTo(before);
  }
  @Test void aiGenericCredentialDenialsDoNotRevealAccountState() throws Exception {
    String known=account(),disabled=account();UUID disabledUser=UUID.fromString(scalar("SELECT user_id FROM identity.app_user WHERE login_name='"+disabled+"'"));
    workflow.setState(disabledUser,CredentialWorkflow.AccountState.DISABLED);
    for(String name:List.of("unknown"+UUID.randomUUID().toString().substring(0,8),known,disabled)) {
      Login f=flow();byte[] body=bytes(Map.of("schemaVersion",1,"expectedContextId",f.view.get("sessionContextId").asString(),"loginName",name,"password",name.equals(known)?"valid length wrong password":PASSWORD));
      var r=call("POST","/api/v1/auth/login",body,headers(f.cookie,f.view.get("csrfToken").asString()));denied(r,401);
      assertThat(WireCodec.strict(r.body()).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }
    assertThat(scalar("SELECT count(*) FROM identity.auth_session WHERE user_id='"+disabledUser+"'")).isEqualTo("0");
  }
  @Test void aiTwoHttpLoginsConsumeOneFlowExactlyOnce() throws Exception {
    Login f=flow();String a=account(),b=account();var gate=new java.util.concurrent.CyclicBarrier(2);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var calls=new ArrayList<java.util.concurrent.Future<Integer>>();
      for(String name:List.of(a,b))calls.add(pool.submit(()->{gate.await();var r=call("POST","/api/v1/auth/login",bytes(Map.of("schemaVersion",1,"expectedContextId",f.view.get("sessionContextId").asString(),"loginName",name,"password",PASSWORD)),headers(f.cookie,f.view.get("csrfToken").asString()));return r.statusCode();}));
      assertThat(List.of(calls.get(0).get(15,TimeUnit.SECONDS),calls.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
      assertThat(scalar("SELECT count(*) FROM identity.auth_session WHERE user_id IN (SELECT user_id FROM identity.app_user WHERE login_name IN ('"+a+"','"+b+"'))")).isEqualTo("1");
    }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
  }
  @Test void ajActivationIsAtomicAndNeverAutoLogsIn() throws Exception {
    var t=workflow.invite("activate"+UUID.randomUUID().toString().substring(0,8));Login f=flow();var r=call("POST","/api/v1/auth/activate",bytes(Map.of("schemaVersion",1,"expectedContextId",f.view.get("sessionContextId").asString(),"token",t.rawToken(),"newPassword",PASSWORD)),headers(f.cookie,f.view.get("csrfToken").asString()));
    assertThat(json(r,200).get("state").asString()).isEqualTo("ACTIVATED");assertThat(r.headers().allValues("Set-Cookie")).isEmpty();assertThat(scalar("SELECT count(*) FROM identity.auth_session WHERE user_id='"+t.userId()+"'")).isEqualTo("0");
  }
  @Test void akResetKeepsPendingStatusAndDeniesOldSessions() throws Exception {
    String name=account();Login a=login(name),f=flow();UUID user=UUID.fromString(a.view.get("userId").asString());var t=workflow.reissue(user,CredentialWorkflow.Purpose.RESET);var h=headers(f.cookie,f.view.get("csrfToken").asString());h.put("Idempotency-Key",UUID.randomUUID().toString());
    byte[] body=bytes(Map.of("schemaVersion",1,"expectedContextId",f.view.get("sessionContextId").asString(),"token",t.rawToken(),"newPassword",PASSWORD));var r=call("POST","/api/v1/auth/password-reset",body,h);String op=json(r,202).get("operationId").asString();assertThat(r.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(json(call("GET","/api/v1/session",null,headers(a.cookie,null)),200).get("state").asString()).isEqualTo("REVOKING");
    assertThat(json(call("GET","/api/v1/session",null,headers(f.cookie,null)),200).get("operationId").asString()).isEqualTo(op);
    denied(call("POST","/api/v1/auth/password-reset",body,h),409);
  }
  @Test void alReauthUnsupportedCannotBecomePasswordOnlySuccess() throws Exception {
    Login a=login(account());denied(call("POST","/api/v1/auth/logout-all",bytes(Map.of("schemaVersion",1,"expectedContextId",a.view.get("sessionContextId").asString(),"reauthProof","unavailable")),headers(a.cookie,a.view.get("csrfToken").asString())),503);
  }
  @ParameterizedTest @ValueSource(strings={"enabled","realm","origin","corpusSha256","throttleKey","dbRunId","unknown"}) void amBadConfigFailsBeforeOpeningListener(String field) throws Exception {
    Properties p=new Properties();p.putAll(session);p.setProperty(field,"invalid");Path path=trustFile.getParent().resolve("session-negative-"+UUID.randomUUID()+".properties");store(path,p);
    assertThatThrownBy(()->SessionRuntime.load(environment(path),settings)).isInstanceOf(IllegalStateException.class).hasMessage("Session runtime configuration/schema unavailable; no protected readiness");
    Path log=path.resolveSibling(path.getFileName()+".runtime.log");
    var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-jar",artifact.toString(),"--spring.profiles.active=session","--backendlab.trust.config="+trustFile,"--backendlab.session.config="+path,"--logging.level.root=ERROR")
        .directory(settings.directory().toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try{assertThat(child.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isNotZero();assertThat(Files.readString(log)).contains("Session runtime configuration/schema unavailable").doesNotContain(session.getProperty("throttleKey"),settings.dbPassword());}
    finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  }
  @Test void anRealmProvisionCannotResetExistingState() throws Exception {assertThatThrownBy(()->provision(UUID.randomUUID().toString())).isInstanceOf(java.sql.SQLException.class);}
  @Test @Timeout(150) void azActualBrowserCookieReorderMultiTabAndOrigin() throws Exception {
    String a=account(),b=account();Path script=root.resolve("scripts/tests/session_browser.mjs");assertThat(script).isRegularFile();
    Path node=Path.of(System.getProperty("user.home"),".cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe");assertThat(node).isRegularFile();
    var builder=new ProcessBuilder(node.toString(),script.toString());builder.directory(root.toFile()).redirectErrorStream(true);
    Process browser=builder.start();
    try {
      var input=Map.of("origin",origin,"directory",settings.directory().toString(),"storePassword",settings.storePassword(),"accountA",a,"accountB",b,"password",PASSWORD);
      try(var out=browser.getOutputStream()){out.write(bytes(new HashMap<>(input)));}
      // Output contains only fixed case labels/version/counts; fixture secrets
      // and response bodies are never written to reports or evidence logs.
      assertThat(browser.waitFor(120,TimeUnit.SECONDS)).as("browser fixture exited").isTrue();
      String output=new String(browser.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
      assertThat(browser.exitValue()).as(output).isZero();
      var report=WireCodec.JSON.readTree(output);assertThat(report.get("status").asString()).isEqualTo("PASS");assertThat(report.get("checks").asInt()).isGreaterThanOrEqualTo(8);
      Files.writeString(settings.directory().resolve("session-browser-report.json"),output,StandardCharsets.UTF_8);
      System.out.println("BROWSER_SESSION actual Chromium checks="+report.get("checks").asInt());
    }finally{if(browser.isAlive()){browser.destroyForcibly();assertThat(browser.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  }
  @Test void zzRestartRetainsCanonicalDenyAndStatusWithoutResurrection() throws Exception {
    Login a=login(account());var h=headers(a.cookie,a.view.get("csrfToken").asString());h.put("Idempotency-Key",UUID.randomUUID().toString());var body=bytes(Map.of("schemaVersion",1,"expectedContextId",a.view.get("sessionContextId").asString()));
    String op=json(call("POST","/api/v1/auth/logout",body,h),202).get("operationId").asString();stop();start();
    assertThat(json(call("GET","/api/v1/session",null,headers(a.cookie,null)),200).get("state").asString()).isEqualTo("REVOKING");
    h.put("X-Session-Context",a.view.get("sessionContextId").asString());assertThat(json(call("GET","/api/v1/auth/operations/"+op,null,h),200).get("state").asString()).isEqualTo("PENDING_FENCES");
    assertThat(json(call("POST","/api/v1/auth/logout",body,h),202).get("operationId").asString()).isEqualTo(op);
  }
}
