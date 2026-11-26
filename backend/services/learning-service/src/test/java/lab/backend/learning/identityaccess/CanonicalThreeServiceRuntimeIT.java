package lab.backend.learning.identityaccess;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import lab.backend.learning.transport.*;
import lab.backend.learning.delivery.DeliveryStore;
import lab.backend.learning.delivery.DeliveryWire;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.*;
import tools.jackson.databind.JsonNode;

/** Owned driver của ba packaged apps/ba DB. Chỉ driver test provision các DB;
 * mỗi process sản phẩm giữ own app role. Identity thật phát admission/proof,
 * Content/Learning thật commit fence, worker thật ghi ACK rồi complete. */
@TestMethodOrder(MethodOrderer.MethodName.class) @Timeout(120)
public class CanonicalThreeServiceRuntimeIT {
  static final List<String> SERVICES=List.of("identity","content","learning");
  // Named browser relay dùng cùng exact Origin với public adapter và Learning;
  // upstream ports18081/18441 vẫn cố định, không arbitrary endpoint override.
  static final String ORIGIN="https://localhost:17441",COOKIE="__Host-backend-lab-session";
  static final String PASSWORD="owned canonical runtime password 🔐";
  static final Properties lab=new Properties();
  static final Map<String,Properties> trust=new LinkedHashMap<>();
  static final Map<String,Process> apps=new LinkedHashMap<>();
  static Path root,directory,corpus;
  static TransportSettings settings;
  static String corpusHash,throttleKey;
  record Login(String cookie,JsonNode view){}
  /** Watchdog có thể cắt trước khi JUnit ghi XML cả class. Chỉ log tên method
   * cố định của source, không displayName/arguments chứa cookie/proof/password.
   * END là boundary cleanup, không là test PASS; XML vẫn là cổng qualification. */
  @BeforeEach void caseStart(TestInfo info){System.out.println("CANONICAL_CASE START "+info.getTestMethod().orElseThrow().getName());}
  @AfterEach void caseEnd(TestInfo info){System.out.println("CANONICAL_CASE END "+info.getTestMethod().orElseThrow().getName());}
  /** Handle thuộc driver; close dừng đúng child ngay cả khi barrier/assert lỗi. */
  record Probe(Process process,BufferedReader output) implements AutoCloseable {
    public void close()throws Exception{
      // Broken pipe khi child đã thoát không được bỏ qua việc dừng đúng
      // process handle. try-with-resources vẫn giữ lỗi close dưới dạng suppressed.
      try{process.getOutputStream().close();}
      finally{
        try{
          if(process.isAlive())process.destroy();
          if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();if(!process.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Owned probe cleanup failed");}
        }finally{output.close();}
      }
    }
  }

  @BeforeAll static void setup()throws Exception{
    String database=System.getProperty("lab.config"),tls=System.getProperty("trust.config");
    if(database==null||tls==null||database.startsWith("${")||tls.startsWith("${"))throw new IllegalStateException("Managed actual PG/TLS required");
    Path databaseFile=Path.of(database).toRealPath(),trustFile=Path.of(tls).toRealPath();directory=trustFile.getParent();
    var marker=WireCodec.strict(Files.readAllBytes(directory.resolve("ownership.json")));
    root=Path.of(WireCodec.string(marker,"workspace")).toRealPath();
    var databaseMarker=WireCodec.strict(Files.readAllBytes(databaseFile.getParent().resolve("ownership.json")));
    if(!root.equals(Path.of(WireCodec.string(databaseMarker,"workspace")).toRealPath())
        ||!databaseFile.startsWith(root.resolve(".tools/db-lab"))||!directory.startsWith(root.resolve(".tools/trust-lab")))throw new IllegalStateException("Owned fixture required");
    try(var reader=Files.newBufferedReader(databaseFile)){lab.load(reader);}
    if(!lab.getProperty("lab.runId").equals(WireCodec.string(databaseMarker,"runId")))throw new IllegalStateException("Owned run mismatch");
    // Trust generator đã bỏ CA signer keys. Driver chỉ đổi named topology;
    // không thêm endpoint tùy ý hoặc tạo cert khi app đang phục vụ.
    for(String signer:List.of("ca.p12","other-ca.p12"))assertThat(Files.exists(directory.resolve(signer))).isFalse();
    for(String service:SERVICES){
      var p=new Properties();try(var reader=Files.newBufferedReader(directory.resolve(service+".properties"))){p.load(reader);}
      if(!lab.getProperty("lab.runId").equals(p.getProperty("dbRunId")))throw new IllegalStateException("Trust DB mismatch");
      p.setProperty("schemaVersion","2");p.setProperty("listenerTopology",TransportSettings.CANONICAL);trust.put(service,p);store(directory.resolve(service+".properties"),p);
    }
    String realm=trust.get("identity").getProperty("realm");
    for(String service:SERVICES){
      if(!realm.equals(trust.get(service).getProperty("realm")))throw new IllegalStateException("Shared realm required");
      Path source=root.resolve("backend/services/"+service+"-service/src");
      Flyway.configure().dataSource(source(service,"migrator")).schemas("migration_metadata").defaultSchema("migration_metadata")
          .createSchemas(false).cleanDisabled(true).locations("filesystem:"+source.resolve("main/resources/db/migration"),"filesystem:"+source.resolve("test/resources/db-test-migration")).load().migrate();
      provision(service);provision(service);
      assertThat(Files.isRegularFile(jar(service))).isTrue();
      // Các SQL probe/maintenance driver chỉ tồn tại trong test-classes.
      try(var archive=new java.util.zip.ZipFile(jar(service).toFile())){
        assertThat(archive.getEntry("BOOT-INF/classes/lab/backend/"+service+"/identityaccess/CanonicalAccessFixtureMain.class")).isNull();
        // Target JAR cũ có thể còn sau targeted unit/test-compile. Kiểm bytes
        // composition/topology hiện hành trước launch để không chạy nhầm profile
        // nền8080/8443 rồi coi startup failure là runtime canonical evidence.
        for(String type:List.of("transport/TransportSettings.class","configuration/BootstrapListenerGuard.class",
            service.equals("identity")?"session/CanonicalRuntime.class":"identityaccess/AdmissionRuntime.class")){
          String relative="lab/backend/"+service+"/"+type;var entry=archive.getEntry("BOOT-INF/classes/"+relative);
          if(entry==null)throw new IllegalStateException("Fresh canonical artifact required");
          try(var bytes=archive.getInputStream(entry)){
            if(!Arrays.equals(bytes.readAllBytes(),Files.readAllBytes(root.resolve("backend/services/"+service+"-service/target/classes/"+relative))))throw new IllegalStateException("Canonical artifact differs from compiled source");
          }
        }
      }
    }
    corpus=directory.resolve("canonical-password-corpus.txt");Files.copy(root.resolve("ops/identity/password-corpus.txt"),corpus);
    corpusHash=WireCodec.strict(Files.readAllBytes(root.resolve("ops/identity/password-corpus.json"))).get("sha256").asString();
    byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);throttleKey=Base64.getEncoder().encodeToString(key);
    var canonical=new Properties();canonical.putAll(Map.ofEntries(Map.entry("schemaVersion","1"),Map.entry("enabled","true"),
        Map.entry("listenerTopology",TransportSettings.CANONICAL),Map.entry("realm",realm),Map.entry("generation",trust.get("identity").getProperty("generation")),
        Map.entry("dbRunId",lab.getProperty("lab.runId")),Map.entry("origin",ORIGIN),Map.entry("corpusPath",corpus.toString()),
        Map.entry("corpusSha256",corpusHash),Map.entry("throttleKey",throttleKey)));
    for(String service:SERVICES)canonical.setProperty(service+"Generation",trust.get(service).getProperty("generation"));
    store(directory.resolve("identity-canonical.properties"),canonical);
    for(String service:List.of("content","learning")){
      var admission=new Properties();admission.putAll(Map.of("schemaVersion","1","enabled","true","realm",realm,
          "generation",trust.get(service).getProperty("generation"),"topologyVersion",AdmissionWire.TOPOLOGY,
          "dbRunId",lab.getProperty("lab.runId"),"identityPort","18441"));
      // Content read hook không có Origin mutation policy: own config exact7
      // fields. Chỉ Learning cần field thứ8 để bind CSRF/Origin trước mutation;
      // không nới parser Content để hợp thức hóa config dùng chung bị sai.
      if(service.equals("learning"))admission.setProperty("origin",ORIGIN);
      store(directory.resolve(service+"-canonical.properties"),admission);
    }
    var env=new StandardEnvironment();env.setActiveProfiles("canonical");env.getPropertySources().addFirst(new MapPropertySource("ownedFixture",Map.of("server.port","18083","backendlab.trust.config",trustFile.toString())));
    settings=TransportSettings.load(env);
    start("content");start("learning");start("identity");
  }
  @AfterAll static void cleanup()throws Exception{
    // Cleanup tiếp cả khi một child đóng lỗi; driver chỉ dừng handles tự tạo.
    Exception failure=null;
    for(String service:SERVICES)try{stop(service);}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
    for(String service:SERVICES)for(int port:new int[]{publicPort(service),privatePort(service)})try{closed(port);}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
    if(failure!=null)throw failure;
  }
  static PGSimpleDataSource source(String service,String kind){
    var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{Integer.parseInt(lab.getProperty("lab.port"))});
    ds.setDatabaseName(service+"_db");ds.setUser(lab.getProperty(service+"."+kind+".user"));ds.setPassword(lab.getProperty(service+"."+kind+".password"));
    ds.setConnectTimeout(1);ds.setSocketTimeout(8);ds.setSslMode("disable");return ds;
  }
  /** Forward provision do operator test gọi trước app; runtime chỉ verify. */
  static void provision(String service)throws Exception{
    try(var connection=source(service,"migrator").getConnection();var statement=connection.prepareStatement("SELECT set_config('backend_lab.provision_realm',?,false),set_config('backend_lab.provision_generation',?,false),set_config('backend_lab.provision_run_id',?,false),set_config('backend_lab.provision_identity_generation',?,false),set_config('backend_lab.provision_content_generation',?,false),set_config('backend_lab.provision_learning_generation',?,false)")){
      statement.setString(1,trust.get(service).getProperty("realm"));statement.setString(2,trust.get(service).getProperty("generation"));statement.setString(3,lab.getProperty("lab.runId"));
      for(int index=0;index<3;index++)statement.setString(index+4,trust.get(SERVICES.get(index)).getProperty("generation"));statement.execute();
      try(var script=connection.createStatement()){
        script.execute(Files.readString(root.resolve("ops/"+service+"/provision-"+(service.equals("identity")?"session":"admission")+"-realm.sql")));
        script.execute(Files.readString(root.resolve("ops/"+service+"/provision-delivery-realm.sql")));
      }
    }
  }
  static void store(Path file,Properties values)throws Exception{try(var writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)){values.store(writer,"owned canonical fixture; secrets never console");}}
  static Path jar(String service){return root.resolve("backend/services/"+service+"-service/target/"+service+"-service-0.1.0-SNAPSHOT.jar");}
  static int publicPort(String service){return switch(service){case "identity"->18081;case "content"->18082;case "learning"->18083;default->throw new IllegalArgumentException();};}
  static int privatePort(String service){return publicPort(service)+360;}
  static void sanitize(ProcessBuilder builder){builder.environment().keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("SPRING_")||k.toUpperCase(Locale.ROOT).startsWith("SERVER_")||k.toUpperCase(Locale.ROOT).startsWith("MANAGEMENT_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("_JAVA_OPTIONS"));}
  /**
   * Tách AF_UNIX selector PipeImpl của từng packaged child khỏi JVM peer.
   * Leaf một ký tự giữ tên socket dài nhất dưới100 bytes trên Windows lab;
   * option nằm trên exact child command nên heap flags inherited vẫn giữ nguyên.
   */
  static String javaIpcOption(String owner) throws Exception {
    if (!Set.of("b", "c", "i", "l", "p").contains(owner)) {
      throw new IllegalArgumentException("Unknown owned Java IPC namespace");
    }
    String runId = lab.getProperty("lab.runId");
    Path expected = root.resolve(".tools/ipc-" + runId.substring(0, 8))
        .toAbsolutePath()
        .normalize();
    String configured = System.getProperty("jdk.net.unixdomain.tmpdir");
    if (configured == null
        || !Path.of(configured).toAbsolutePath().normalize().equals(expected)) {
      throw new IllegalStateException("Owned Java IPC root required");
    }
    Path child = expected.resolve(owner);
    int longestSocketBytes = child.toString().getBytes(StandardCharsets.UTF_8).length
        + 1
        + "socket_2147483647".length();
    if (longestSocketBytes > 100) {
      throw new IllegalStateException("Owned Java IPC path exceeds qualified budget");
    }
    Files.createDirectories(child);
    return "-Djdk.net.unixdomain.tmpdir=" + child;
  }
  static void start(String service) throws Exception {
    String property = service.equals("identity") ? "canonical" : "admission";
    Path canonicalLog = directory.resolve(service + "-canonical.log");
    var builder = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
        javaIpcOption(service.substring(0, 1)),
        "-jar", jar(service).toString(),
        "--spring.profiles.active=canonical",
        "--server.port=" + publicPort(service),
        "--backendlab.trust.config=" + directory.resolve(service + ".properties"),
        "--backendlab." + property + ".config="
            + directory.resolve(service + "-canonical.properties"),
        "--logging.level.root=ERROR");
    builder.directory(directory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(
            canonicalLog.toFile()));
    sanitize(builder);

    // Exact child được retry một lần chỉ khi fail-closed trước readiness.
    // Timeout còn sống không retry/nới budget; product config và clock gate giữ nguyên.
    for (int attempt = 1; attempt <= 2; attempt++) {
      for (int port : new int[]{publicPort(service), privatePort(service)}) {
        closed(port);
      }
      long clockRejectionsBefore = Files.exists(canonicalLog)
          ? Files.readString(canonicalLog).lines()
              .filter(line -> line.contains(
                  "Transport clock unavailable or outside verified budget"))
              .count()
          : 0;
      Process process = builder.start();
      apps.put(service, process);
      long deadline = System.nanoTime() + Duration.ofSeconds(35).toNanos();
      String lastProbe = "NOT_ATTEMPTED";
      boolean retryClockReadiness = false;
      while (System.nanoTime() < deadline) {
        if (!process.isAlive()) {
          int exit = process.exitValue();
          apps.remove(service, process);
          long clockRejectionsAfter = Files.readString(canonicalLog).lines()
              .filter(line -> line.contains(
                  "Transport clock unavailable or outside verified budget"))
              .count();
          boolean clockReadinessRejected = clockRejectionsAfter > clockRejectionsBefore;
          String category = clockReadinessRejected
              ? "TRANSPORT_CLOCK_READINESS"
              : "OTHER_STARTUP_EXIT";
          if (attempt == 1 && clockReadinessRejected) {
            System.out.println(
                "CANONICAL_START_RETRY service=" + service
                    + " firstExit=" + exit
                    + " probe=" + lastProbe
                    + " category=" + category);
            retryClockReadiness = true;
            break;
          }
          throw new IllegalStateException(
              "Owned canonical app exited service=" + service
                  + " exit=" + exit
                  + " probe=" + lastProbe
                  + " category=" + category
                  + "; inspect retained log");
        }
        try {
          String path = service.equals("identity")
              ? "/api/v1/session"
              : "/api/v1/transport/status";
          int status = call(
              service,
              "edge",
              publicPort(service),
              "GET",
              path,
              null,
              Map.of("X-Backend-Lab-Client-IP", "127.0.0.1"))
              .statusCode();
          lastProbe = "HTTP_" + status;
          if (status == 200) {
            return;
          }
        } catch (java.io.IOException startup) {
          // Chỉ giữ class cố định; message có thể chứa endpoint/config của lab.
          lastProbe = startup.getClass().getSimpleName();
        }
        Thread.sleep(100);
      }
      if (retryClockReadiness) {
        continue;
      }
      if (process.isAlive()) {
        throw new IllegalStateException(
            "Canonical startup timeout service=" + service
                + " processAlive=true"
                + " probe=" + lastProbe);
      }
      throw new IllegalStateException(
          "Owned canonical app exited service=" + service
              + " exit=" + process.exitValue()
              + " probe=" + lastProbe
              + " category=EXIT_AFTER_WAIT"
              + "; inspect retained log");
    }
    throw new IllegalStateException("Canonical startup retry exhausted service=" + service);
  }
  static void stop(String service)throws Exception{var process=apps.remove(service);if(process!=null){process.destroy();if(!process.waitFor(8,TimeUnit.SECONDS)){process.destroyForcibly();if(!process.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Owned child cleanup failed");}}}
  static void closed(int port)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();int refused=0;while(System.nanoTime()<deadline){try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),150);refused=0;}catch(java.io.IOException expected){if(++refused==2)return;}Thread.sleep(50);}throw new IllegalStateException("Named listener still reachable");}
  static HttpResponse<byte[]> call(String receiver,String caller,int port,String method,String path,byte[] body,Map<String,String> headers)throws Exception{
    try(var client=client(receiver,caller)){return call(client,port,method,path,body,headers);}
  }
  /** Driver có thể chuẩn bị TLS ngoài held transaction; vẫn dùng cert/hostname
   * kiểm thật, không bypass mTLS hoặc thay caller trong lúc tái dùng client. */
  static HttpClient client(String receiver,String caller)throws Exception{
    return HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,directory.resolve(caller+".p12"),receiver)).connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
  }
  static HttpResponse<byte[]> call(HttpClient client,int port,String method,String path,byte[] body,Map<String,String> headers)throws Exception{
    var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));if(body!=null)request.header("Content-Type","application/json");headers.forEach(request::header);
    return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
  }
  static Map<String,String> headers(Login login){var headers=new HashMap<String,String>();headers.put("X-Backend-Lab-Client-IP","127.0.0.1");headers.put("Origin",ORIGIN);if(login!=null){headers.put("Cookie",COOKIE+"="+login.cookie);headers.put("X-CSRF-TOKEN",login.view.get("csrfToken").asString());}return headers;}
  static byte[] bytes(Map<String,Object> value){return WireCodec.JSON.writeValueAsBytes(value);}
  static JsonNode json(HttpResponse<byte[]> response,int status){assertThat(response.statusCode()).isEqualTo(status);assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");return WireCodec.strict(response.body());}
  static Login login()throws Exception{
    return login(account(),PASSWORD);
  }
  /** Login account đã được operator cấp, để kiểm reset với password mới mà
   * không tạo user khác hoặc ghi fixture trực tiếp vào credential store. */
  static Login login(String name,String password)throws Exception{
    var flowHeaders=headers(null);flowHeaders.put("X-Auth-Flow","credential");var flow=call("identity","edge",18081,"POST","/api/v1/session",bytes(Map.of("schemaVersion",1)),flowHeaders);
    var preauth=new Login(cookie(flow),json(flow,200));
    var response=call("identity","edge",18081,"POST","/api/v1/auth/login",bytes(Map.of("schemaVersion",1,"expectedContextId",preauth.view.get("sessionContextId").asString(),"loginName",name,"password",password)),headers(preauth));
    return new Login(cookie(response),json(response,200));
  }
  /** Tạo account bằng CLI credential đã có; token/password chỉ stdin/RAM,
   * không dùng fixture trực tiếp ghi hash hoặc public bootstrap tự provision. */
  static String account()throws Exception {
    String name="canonical"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    String invitation=maintenance("invite",name,null),token=null;for(String line:invitation.lines().toList())if(line.startsWith("token="))token=line.substring(6);
    if(token==null)throw new IllegalStateException("Operator token missing");
    if(!maintenance("activate","stdin",token+"\n"+PASSWORD+"\n").trim().equals("VERIFIED"))throw new IllegalStateException("Operator activation unavailable");
    return name;
  }
  static String cookie(HttpResponse<byte[]> response){var values=response.headers().allValues("Set-Cookie");assertThat(values.size()).isEqualTo(1);String cookie=values.getFirst().split(";",2)[0];if(!cookie.startsWith(COOKIE+"="))throw new IllegalStateException("Auth cookie missing");return cookie.substring(COOKIE.length()+1);}
  /** Secret CLI stdout giữ trong RAM, stderr có bounded diagnostic riêng. */
  static String maintenance(String action,String argument,String stdin)throws Exception{
    var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend.identity.credential.IdentityMaintenanceCli","-cp",jar("identity").toString(),"org.springframework.boot.loader.launch.PropertiesLauncher",action,argument);
    var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectError(directory.resolve("maintenance-"+UUID.randomUUID()+".log").toFile());sanitize(builder);
    builder.environment().putAll(Map.of("IDENTITY_DB_URL","jdbc:postgresql://127.0.0.1:"+lab.getProperty("lab.port")+"/identity_db","IDENTITY_DB_USER","identity_app","IDENTITY_DB_PASSWORD",lab.getProperty("identity.app.password"),"IDENTITY_CORPUS_PATH",corpus.toString(),"IDENTITY_CORPUS_SHA256",corpusHash,"IDENTITY_THROTTLE_KEY",throttleKey));
    var process=builder.start();try{try(var input=process.getOutputStream()){if(stdin!=null)input.write(stdin.getBytes(StandardCharsets.UTF_8));}if(!process.waitFor(25,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalStateException("Owned maintenance unavailable");return new String(process.getInputStream().readNBytes(4097),StandardCharsets.UTF_8);}finally{if(process.isAlive()){process.destroyForcibly();if(!process.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Owned CLI cleanup failed");}}
  }
  static Probe startProbe(String service,Login login,UUID probe,String mode)throws Exception{
    Map<String,Object> request=new LinkedHashMap<>(Map.of("schemaVersion",1,"sessionCredential",login.cookie,"expectedContextId",login.view.get("sessionContextId").asString(),"probeId",probe.toString()));
    if(service.equals("content")){request.put("topicId","canonical-topic");request.put("unitId","canonical-unit");}
    else{request.put("mode",mode);request.put("draftId",UUID.randomUUID().toString());request.put("csrfToken",login.view.get("csrfToken").asString());request.put("origin",ORIGIN);request.put("bodyBase64",Base64.getEncoder().encodeToString("{\"revision\":1}".getBytes(StandardCharsets.UTF_8)));}
    var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend."+service+".identityaccess.CanonicalAccessFixtureMain","-Dloader.path="+root.resolve("backend/services/"+service+"-service/target/test-classes"),"-cp",jar(service).toString(),"org.springframework.boot.loader.launch.PropertiesLauncher",directory.resolve(service+".properties").toString(),directory.resolve(service+"-canonical.properties").toString());
    var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectError(directory.resolve("access-probe-"+probe+".log").toFile());sanitize(builder);var process=builder.start();
    try{var input=process.getOutputStream();input.write(bytes(request));input.write('\n');input.flush();return new Probe(process,new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)));}
    catch(Exception failure){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw failure;}
  }
  /** Bounded event read; timeout cleanup đóng child/pipe để reader kết thúc. */
  static JsonNode event(Probe probe)throws Exception{
    var result=new CompletableFuture<String>();Thread.ofVirtual().start(()->{try{result.complete(probe.output.readLine());}catch(Exception failure){result.completeExceptionally(failure);}});
    String line=result.get(10,TimeUnit.SECONDS);if(line==null||line.length()>8192)throw new IllegalStateException("Owned probe event missing");return WireCodec.strict(line.getBytes(StandardCharsets.UTF_8));
  }
  static JsonNode probe(String service,Login login,UUID id)throws Exception{
    try(var probe=startProbe(service,login,id,"MUTATE")){
      probe.process.getOutputStream().close();JsonNode result=event(probe);
      if(!probe.process.waitFor(20,TimeUnit.SECONDS)||probe.process.exitValue()!=0)throw new IllegalStateException("Owned access probe unavailable");return result;
    }
  }
  static long count(String service,String table,String column,String value)throws Exception{try(var connection=source(service,"app").getConnection();var statement=connection.prepareStatement("SELECT count(*) FROM "+service+"."+table+" WHERE "+column+"::text=?")){statement.setString(1,value);try(var rows=statement.executeQuery()){rows.next();return rows.getLong(1);}}}
  static String state(String operation)throws Exception{try(var connection=source("identity","app").getConnection();var statement=connection.prepareStatement("SELECT state FROM identity.auth_operation WHERE operation_id=?")){statement.setObject(1,UUID.fromString(operation));try(var rows=statement.executeQuery()){if(!rows.next())throw new IllegalStateException("Operation missing");return rows.getString(1);}}}
  static byte[] command(String operation)throws Exception{try(var connection=source("identity","app").getConnection();var statement=connection.prepareStatement("SELECT command_bytes FROM identity.auth_delivery_outbox WHERE operation_id=? AND consumer='learning'")){statement.setObject(1,UUID.fromString(operation));try(var rows=statement.executeQuery()){if(!rows.next())throw new IllegalStateException("Exact command missing");return rows.getBytes(1);}}}
  static void awaitCompleted(String operation)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();while(System.nanoTime()<deadline){if(state(operation).equals("COMPLETED"))return;Thread.sleep(100);}throw new IllegalStateException("Barrier remains pending");}

  @Test @Tag("SEC-01") void aaCanonicalProviderDrivesBothOwnAccessHooks()throws Exception{
    Login login=login();assertThat(probe("content",login,UUID.randomUUID()).get("state").asString()).isEqualTo("READ_COMMITTED");
    UUID effect=UUID.randomUUID();assertThat(probe("learning",login,effect).get("state").asString()).isEqualTo("COMMITTED");
    assertThat(count("learning","transaction_probe","id",effect.toString())).isEqualTo(1);
  }
  @Test @Tag("SEC-09") void abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier()throws Exception{
    Login login=login();stop("learning");
    try{
      var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
      var response=call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString())),headers);
      String operation=json(response,202).get("operationId").asString();assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
      long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(count("content","auth_command_receipt","operation_id",operation)==0&&System.nanoTime()<deadline)Thread.sleep(100);
      assertThat(count("content","auth_command_receipt","operation_id",operation)).isEqualTo(1);assertThat(state(operation)).isEqualTo("PENDING_FENCES");
      start("learning");awaitCompleted(operation);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      assertThat(probe("content",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
      UUID late=UUID.randomUUID();assertThat(probe("learning",login,late).get("state").asString()).isEqualTo("DENIED");assertThat(count("learning","transaction_probe","id",late.toString())).isZero();
    }finally{if(!apps.containsKey("learning"))start("learning");}
  }
  @Test @Tag("SEC-19") void acPublicProofAndLearningOnlyReservationAreDurablyBound()throws Exception{
    Login login=login();String key=UUID.randomUUID().toString(),intent="a".repeat(64);var headers=headers(login);headers.put("Idempotency-Key",key);
    var issued=call("identity","edge",18081,"POST","/api/v1/auth/reauth",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString(),"password",PASSWORD,"purpose","LEARNING_RESET","intentHash",intent)),headers);
    JsonNode proof=json(issued,200);assertThat(issued.headers().allValues("Set-Cookie")).isEmpty();assertThat(proof.get("proof").asString().matches("[A-Za-z0-9_-]{43}")).isTrue();
    // Bind sessionRef từ canonical DB theo server context; public view không
    // cần phát private reference. Chỉ driver test có quyền đọc để dựng INT06.
    String session;try(var connection=source("identity","app").getConnection();var statement=connection.prepareStatement("SELECT session_ref FROM identity.auth_session WHERE context_id=?")){statement.setObject(1,UUID.fromString(login.view.get("sessionContextId").asString()));try(var rows=statement.executeQuery()){if(!rows.next())throw new IllegalStateException("Canonical session missing");session=rows.getString(1);}}
    var request=Map.<String,Object>of("schemaVersion",1,"proof",proof.get("proof").asString(),"userId",login.view.get("userId").asString(),"sessionRef",session,"sessionContextId",login.view.get("sessionContextId").asString(),"credentialEpoch",login.view.get("credentialEpoch").asString(),"authRealmId",trust.get("identity").getProperty("realm"),"purpose","LEARNING_RESET","operationKey",key,"intentHash",intent);
    String path="/internal/v1/reauth-authorizations/reservations";
    assertThat(call("identity","content",18441,"POST",path,bytes(request),Map.of()).statusCode()).isEqualTo(403);
    var first=call("identity","learning",18441,"POST",path,bytes(request),Map.of());assertThat(json(first,200).get("state").asString()).isEqualTo("RESERVED");
    var retry=call("identity","learning",18441,"POST",path,bytes(request),Map.of());json(retry,200);assertThat(Arrays.equals(first.body(),retry.body())).isTrue();
    assertThat(count("identity","auth_reauth_grant","grant_ref",proof.get("grantRef").asString())).isEqualTo(1);
  }
  @ParameterizedTest @ValueSource(strings={"HOLD_COMMIT","HOLD_ROLLBACK"}) @Tag("SEC-02")
  void adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback(String mode)throws Exception{
    Login login=login();UUID effect=UUID.randomUUID();String operation;
    var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
    byte[] body=bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString()));
    // Cert/TLS handshake và SCRAM (xác thực DB) chuẩn bị trước HELD. Callback
    // vẫn chỉ chờ RELEASE tối đa4s trong budget6s, không nới timeout sản phẩm.
    try(var client=client("identity","edge");var learning=source("learning","app").getConnection();var identity=source("identity","app").getConnection();
        var blocking=learning.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE datname='learning_db' AND usename='learning_app' AND query LIKE '%auth_account_gate%' AND ? = ANY(pg_blocking_pids(pid))");
        var pending=identity.prepareStatement("SELECT state FROM identity.auth_operation WHERE operation_id=?");
        var receipt=learning.prepareStatement("SELECT count(*) FROM learning.auth_command_receipt WHERE operation_id=?")){
      assertThat(json(call(client,18081,"GET","/api/v1/session",null,headers(login)),200).get("sessionContextId").asString()).isEqualTo(login.view.get("sessionContextId").asString());
      try(var probe=startProbe("learning",login,effect,mode)){
        JsonNode held=event(probe);assertThat(held.get("state").asString()).isEqualTo("HELD");
        long heldAt=System.nanoTime();
        int backendPid=held.get("backendPid").asInt();assertThat(backendPid).isPositive();blocking.setInt(1,backendPid);
        var response=call(client,18081,"POST","/api/v1/auth/logout",body,headers);
        operation=json(response,202).get("operationId").asString();
        System.out.println("CANONICAL_BARRIER logoutMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-heldAt));
        // Auto-commit observation lấy tình trạng lock mới mỗi query. Chỉ PID
        // giữ đúng mutation gate được tính; pending alone không là lock proof.
        boolean blocked=false;long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
        while(System.nanoTime()<deadline){
          try(var rows=blocking.executeQuery()){rows.next();if(rows.getLong(1)>0){blocked=true;break;}}
          Thread.sleep(10);
        }
        Throwable observationFailure=null;
        try{
          assertThat(blocked).as("Receiver fence observed waiting for mutation gate").isTrue();
          pending.setObject(1,UUID.fromString(operation));receipt.setObject(1,UUID.fromString(operation));
          try(var rows=pending.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("PENDING_FENCES");}
          try(var rows=receipt.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();}
        }catch(Exception|AssertionError failure){observationFailure=failure;throw failure;}
        finally{
          // Giữ assertion gốc nếu child đã hết lifetime trước RELEASE. Lỗi
          // release vẫn làm FAIL khi observation đạt; không coi timeout là rollback mong đợi.
          System.out.println("CANONICAL_BARRIER releaseMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-heldAt));
          try{probe.process.getOutputStream().write("RELEASE\n".getBytes(StandardCharsets.US_ASCII));probe.process.getOutputStream().flush();}
          catch(java.io.IOException releaseFailure){if(observationFailure!=null)observationFailure.addSuppressed(releaseFailure);else throw releaseFailure;}
        }
        // Explicit release tách requested rollback khỏi callback timeout; cả
        // hai modes phải chứng minh gate giữ tới commit/rollback thật phía sau.
        assertThat(event(probe).get("state").asString()).isEqualTo("RELEASED");
        assertThat(event(probe).get("state").asString()).isEqualTo(mode.equals("HOLD_COMMIT")?"COMMITTED":"DENIED");
        if(!probe.process.waitFor(5,TimeUnit.SECONDS)||probe.process.exitValue()!=0)throw new IllegalStateException("Owned held probe unavailable");
      }
    }
    awaitCompleted(operation);assertThat(count("learning","transaction_probe","id",effect.toString())).isEqualTo(mode.equals("HOLD_COMMIT")?1:0);
    // Sau actual dual ACK/Identity completion, request mới cùng handle phải
    // deny trước local effect. Thứ tự commit đã được chứng minh qua shared gate.
    UUID late=UUID.randomUUID();assertThat(probe("learning",login,late).get("state").asString()).isEqualTo("DENIED");assertThat(count("learning","transaction_probe","id",late.toString())).isZero();
  }
  @Test @Tag("SEC-09") void aeLostAckAndBothProcessRestartsReuseExactDurableCommand()throws Exception{
    Login login=login();stop("learning");
    try{
      var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
      var response=call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString())),headers);
      String operation=json(response,202).get("operationId").asString();byte[] exact=command(operation);
      stop("identity");start("learning");
      // Actual receiver commit nhưng ACK không được giao cho Identity store:
      // coordinator process đã dừng. Không tạo ACK fixture hoặc giả timeout.
      String path=AdmissionWire.FENCE_PATH+operation;
      var first=call("learning","identity",18443,"PUT",path,exact,Map.of());json(first,200);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      assertThat(state(operation)).isEqualTo("PENDING_FENCES");
      stop("learning");start("learning");
      var duplicate=call("learning","identity",18443,"PUT",path,exact,Map.of());json(duplicate,200);
      assertThat(Arrays.equals(first.body(),duplicate.body())).isTrue();
      start("identity");awaitCompleted(operation);
      assertThat(Arrays.equals(command(operation),exact)).isTrue();
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      assertThat(probe("learning",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
    }finally{
      if(!apps.containsKey("learning"))start("learning");
      if(!apps.containsKey("identity"))start("identity");
    }
  }
  /** Public TLS thật cho cả hai Identity sensitive actions. Learning down giữ
   * PENDING; restart Identity đọc cùng consumed proof/key, không hash/bump lần2.
   * Sau hai receiver commits, retry A với cookie B không revoke/clear phiên B. */
  @ParameterizedTest @ValueSource(strings={"LOGOUT_ALL","PASSWORD_CHANGE"}) @Tag("SEC-19")
  void afActualSensitiveProofAndAccountBarrierSurviveRestart(String purpose)throws Exception {
    Login original=login();String context=original.view.get("sessionContextId").asString();
    String user=original.view.get("userId").asString(),name;
    try(var c=source("identity","app").getConnection();var s=c.prepareStatement("SELECT login_name FROM identity.app_user WHERE user_id=?::uuid")) {
      s.setString(1,user);try(var r=s.executeQuery()){r.next();name=r.getString(1);}
    }
    String key=UUID.randomUUID().toString(),password=purpose.equals("PASSWORD_CHANGE")?"owned changed password 🔐\nexact payload":null;
    String intent=sensitiveIntent(purpose,context,key,password);var headers=headers(original);headers.put("Idempotency-Key",key);
    var mint=call("identity","edge",18081,"POST","/api/v1/auth/reauth",bytes(Map.of("schemaVersion",1,"expectedContextId",context,"password",PASSWORD,"purpose",purpose,"intentHash",intent)),headers);
    JsonNode proof=json(mint,200);assertThat(mint.headers().allValues("Set-Cookie")).isEmpty();
    var body=new LinkedHashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",context,"reauthProof",proof.get("proof").asString()));
    if(password!=null)body.put("newPassword",password);
    String path="/api/v1/auth/"+(purpose.equals("LOGOUT_ALL")?"logout-all":"password-change");
    var wrong=new HashMap<>(headers);wrong.put("X-CSRF-TOKEN","x".repeat(43));
    assertThat(call("identity","edge",18081,"POST",path,bytes(body),wrong).statusCode()).isEqualTo(403);
    stop("learning");
    try {
      var started=call("identity","edge",18081,"POST",path,bytes(body),headers);
      String operation=json(started,202).get("operationId").asString();assertThat(started.headers().allValues("Set-Cookie")).isEmpty();
      assertThat(state(operation)).isEqualTo("PENDING_FENCES");
      var retry=call("identity","edge",18081,"POST",path,bytes(body),headers);
      assertThat(json(retry,202).get("operationId").asString()).isEqualTo(operation);
      // Mô phỏng mất response ở client bằng retry sau process restart; stable
      // configured key và durable grant giữ exact payload/operation qua restart.
      stop("identity");start("identity");
      var resumed=call("identity","edge",18081,"POST",path,bytes(body),headers);
      assertThat(json(resumed,202).get("operationId").asString()).isEqualTo(operation);
      assertThat(resumed.headers().allValues("Set-Cookie")).isEmpty();
      start("learning");awaitCompleted(operation);
      assertThat(count("content","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      var completed=call("identity","edge",18081,"POST",path,bytes(body),headers);
      json(completed,200);assertThat(completed.headers().allValues("Set-Cookie")).isEmpty();
      try(var c=source("identity","app").getConnection();var s=c.prepareStatement("SELECT u.credential_epoch,u.auth_transition_pending,g.state,(SELECT count(*) FROM identity.security_audit a WHERE a.correlation_id=g.grant_ref AND a.event_type='REAUTH_CONSUMED') FROM identity.app_user u JOIN identity.auth_reauth_grant g ON g.user_id=u.user_id WHERE g.grant_ref=?::uuid")) {
        s.setString(1,proof.get("grantRef").asString());try(var r=s.executeQuery()){
          assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(2);assertThat(r.getBoolean(2)).isFalse();assertThat(r.getString(3)).isEqualTo("CONSUMED");assertThat(r.getLong(4)).isEqualTo(1);
        }
      }
      var flowHeaders=headers(original);flowHeaders.put("X-Auth-Flow","credential");
      var flow=call("identity","edge",18081,"POST","/api/v1/session",bytes(Map.of("schemaVersion",1)),flowHeaders);
      var preauth=new Login(cookie(flow),json(flow,200));
      var logged=call("identity","edge",18081,"POST","/api/v1/auth/login",bytes(Map.of("schemaVersion",1,"expectedContextId",preauth.view.get("sessionContextId").asString(),"loginName",name,"password",password==null?PASSWORD:password)),headers(preauth));
      var next=new Login(cookie(logged),json(logged,200));assertThat(next.view.get("credentialEpoch").asString()).isEqualTo("2");
      var wrongCookie=headers(next);wrongCookie.put("Idempotency-Key",key);
      var mismatch=call("identity","edge",18081,"POST",path,bytes(body),wrongCookie);
      json(mismatch,409);assertThat(mismatch.headers().allValues("Set-Cookie")).isEmpty();
      assertThat(json(call("identity","edge",18081,"GET","/api/v1/session",null,headers(next)),200).get("state").asString()).isEqualTo("ACTIVE");
      assertThat(probe("learning",original,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
      assertThat(probe("content",next,UUID.randomUUID()).get("state").asString()).isEqualTo("READ_COMMITTED");
    }finally{
      if(!apps.containsKey("identity"))start("identity");
      if(!apps.containsKey("learning"))start("learning");
    }
  }

  /** Client-side framing độc lập trong driver; không import Identity helper hay
   * shared domain JAR. Raw password/hash chỉ RAM/request Identity, không log. */
  static String sensitiveIntent(String purpose,String context,String key,String password)throws Exception {
    var hash=java.security.MessageDigest.getInstance("SHA-256");
    hash.update("identity-sensitive-intent-v1".getBytes(StandardCharsets.UTF_8));hash.update((byte)0);
    for(String field:List.of(purpose,context,key,password==null?"":password)) {
      byte[] bytes=field.getBytes(StandardCharsets.UTF_8);
      hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);
    }
    return HexFormat.of().formatHex(hash.digest());
  }

  /** Canonical provider thật qua private mTLS: parser deny không đổi idle,
   * unknown cookie/context/CSRF không cấp ALLOW. Identity down không dùng cache. */
  @Test @Tag("SEC-19") @Tag("SEC-20") @Tag("SEC-10")
  void agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings()throws Exception {
    Login login=login();String path="/internal/v1/auth-context/admissions";
    Map<String,Object> read=admissionRequest(login,false),mutation=admissionRequest(login,true);
    JsonNode allowed=json(call("identity","content",18441,"POST",path,bytes(read),Map.of()),200);
    assertThat(allowed.get("requestNonce").asString()).isEqualTo(read.get("requestNonce"));
    assertThat(allowed.get("userId").asString()).isEqualTo(login.view.get("userId").asString());
    assertThat(allowed.get("requestBinding").asString()).isEqualTo(read.get("requestBinding"));
    String idle=identityValue("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+login.view.get("sessionContextId").asString()+"'");
    deny(call("identity","identity",18441,"POST",path,bytes(read),Map.of()),403,login);
    deny(call("identity","content",18441,"POST",path,bytes(read),Map.of("Cookie","forged")),403,login);
    deny(call("identity","content",18081,"POST",path,bytes(read),Map.of()),403,login);
    for(String field:List.of("audience","action","requestBinding","expectedContextId","sessionCredential","userId")) {
      var wrong=new LinkedHashMap<>(read);
      wrong.put(field,switch(field){case "audience"->"learning";case "action"->"LEARNING_DRAFT_SAVE";case "requestBinding"->"0".repeat(64);case "sessionCredential"->"x".repeat(43);default->UUID.randomUUID().toString();});
      deny(call("identity","content",18441,"POST",path,bytes(wrong),Map.of()),switch(field){case "expectedContextId"->409;case "sessionCredential"->401;case "userId"->400;default->403;},login);
    }
    for(String field:List.of("csrfToken","origin")) {
      var wrong=new LinkedHashMap<>(mutation);wrong.put(field,field.equals("csrfToken")?"x".repeat(43):"https://wrong.example");
      deny(call("identity","learning",18441,"POST",path,bytes(wrong),Map.of()),403,login);
    }
    assertThat(identityValue("SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+login.view.get("sessionContextId").asString()+"'")).isEqualTo(idle);
    // Operator-only realm mismatch fixture, không restore/provision runtime.
    String realm=trust.get("identity").getProperty("realm");
    try {
      fixtureSql("identity","UPDATE identity.auth_environment SET realm_id='"+UUID.randomUUID()+"'");
      deny(call("identity","content",18441,"POST",path,bytes(read),Map.of()),503,login);
    }finally{fixtureSql("identity","UPDATE identity.auth_environment SET realm_id='"+realm+"'");}
    stop("identity");
    try {
      assertThat(probe("content",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
      UUID effect=UUID.randomUUID();assertThat(probe("learning",login,effect).get("state").asString()).isEqualTo("DENIED");
      assertThat(count("learning","transaction_probe","id",effect.toString())).isZero();
    }finally{start("identity");}
    assertThat(probe("content",login,UUID.randomUUID()).get("state").asString()).isEqualTo("READ_COMMITTED");
  }

  /** Mọi INT06 binding được kiểm qua TLS thật trước reserve; restart đọc lại
   * cùng reference/TTL. Expired grant là short-lived operator fixture đã qua
   * insert guard rồi hết hạn theo DB clock, không sửa TTL5phút của API-I08. */
  @Test @Tag("SEC-23") @Tag("SEC-20")
  void ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart()throws Exception {
    Login login=login();String key=UUID.randomUUID().toString(),intent="b".repeat(64),path="/internal/v1/reauth-authorizations/reservations";
    JsonNode proof=mint(login,key,"LEARNING_RESET",intent);var request=reservationRequest(login,proof.get("proof").asString(),key,intent);
    String grant=proof.get("grantRef").asString();
    for(String field:List.of("userId","sessionRef","sessionContextId","authRealmId","operationKey","credentialEpoch","intentHash")) {
      var wrong=new LinkedHashMap<>(request);wrong.put(field,field.equals("credentialEpoch")?"2":field.equals("intentHash")?"c".repeat(64):UUID.randomUUID().toString());
      deny(call("identity","learning",18441,"POST",path,bytes(wrong),Map.of()),409,login);
    }
    var wrongPurpose=new LinkedHashMap<>(request);wrongPurpose.put("purpose","LOGOUT_ALL");
    deny(call("identity","learning",18441,"POST",path,bytes(wrongPurpose),Map.of()),400,login);
    deny(call("identity","content",18441,"POST",path,bytes(request),Map.of()),403,login);
    assertThat(identityValue("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+grant+"'")).isEqualTo("ISSUED");
    String expired=expiredProofFixture(grant);var expiredRequest=new LinkedHashMap<>(request);expiredRequest.put("proof",expired);
    assertThat(identityValue("SELECT count(*) FROM identity.auth_reauth_grant WHERE proof_digest=decode('"+sha(expired.getBytes(StandardCharsets.US_ASCII))+"','hex') AND expires_at<clock_timestamp() AND state='ISSUED'")).isEqualTo("1");
    deny(call("identity","learning",18441,"POST",path,bytes(expiredRequest),Map.of()),401,login);
    var first=call("identity","learning",18441,"POST",path,bytes(request),Map.of());JsonNode reserved=json(first,200);
    stop("identity");start("identity");
    var retry=call("identity","learning",18441,"POST",path,bytes(request),Map.of());JsonNode repeated=json(retry,200);
    // JSON object không có thứ tự field; Map.of có thể đổi thứ tự sau JVM
    // restart. So toàn bộ tree vẫn buộc đúng mọi binding/reference/TTL, rồi
    // kiểm DB không reserve lần hai; không nới immutable command bytes INT02.
    assertThat(repeated).isEqualTo(reserved);
    assertThat(reserved.get("grantRef").asString()).isEqualTo(grant);
    var different=new LinkedHashMap<>(request);different.put("operationKey",UUID.randomUUID().toString());
    deny(call("identity","learning",18441,"POST",path,bytes(different),Map.of()),409,login);
    assertThat(identityValue("SELECT count(*) FROM identity.security_audit WHERE correlation_id='"+grant+"' AND event_type='REAUTH_RESERVED'")).isEqualTo("1");
  }

  /** Proof Identity sensitive không dùng được cho Learning hoặc action/payload
   * khác. Negative attempts giữ ISSUED/epoch1; correct effect sau đó làm proof
   * Learning epoch cũ unusable, không cấp quyền qua retry status. */
  @ParameterizedTest @ValueSource(strings={"LOGOUT_ALL","PASSWORD_CHANGE"}) @Tag("SEC-23") @Tag("SEC-19")
  void aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls(String purpose)throws Exception {
    Login login=login();String context=login.view.get("sessionContextId").asString(),key=UUID.randomUUID().toString();
    String password=purpose.equals("PASSWORD_CHANGE")?"canonical negative bound password 🔐":null;
    JsonNode proof=mint(login,key,purpose,sensitiveIntent(purpose,context,key,password));
    String learningKey=UUID.randomUUID().toString(),learningIntent="d".repeat(64);
    JsonNode learning=mint(login,learningKey,"LEARNING_RESET",learningIntent);var reservation=reservationRequest(login,learning.get("proof").asString(),learningKey,learningIntent);
    var body=new LinkedHashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",context,"reauthProof",proof.get("proof").asString()));
    if(password!=null)body.put("newPassword",password);
    String path=purpose.equals("LOGOUT_ALL")?"/api/v1/auth/logout-all":"/api/v1/auth/password-change";
    var headers=headers(login);headers.put("Idempotency-Key",key);
    var wrongKey=new HashMap<>(headers);wrongKey.put("Idempotency-Key",UUID.randomUUID().toString());
    deny(call("identity","edge",18081,"POST",path,bytes(body),wrongKey),409,login);
    var wrongContext=new LinkedHashMap<>(body);wrongContext.put("expectedContextId",UUID.randomUUID().toString());
    deny(call("identity","edge",18081,"POST",path,bytes(wrongContext),headers),409,login);
    var wrongOrigin=new HashMap<>(headers);wrongOrigin.put("Origin","https://wrong.example");
    deny(call("identity","edge",18081,"POST",path,bytes(body),wrongOrigin),403,login);
    var wrongCsrf=new HashMap<>(headers);wrongCsrf.put("X-CSRF-TOKEN","x".repeat(43));
    deny(call("identity","edge",18081,"POST",path,bytes(body),wrongCsrf),403,login);
    var wrongProof=new LinkedHashMap<>(body);wrongProof.put("reauthProof",learning.get("proof").asString());
    deny(call("identity","edge",18081,"POST",path,bytes(wrongProof),headers),409,login);
    if(password!=null){var different=new LinkedHashMap<>(body);different.put("newPassword",password+" changed");deny(call("identity","edge",18081,"POST",path,bytes(different),headers),409,login);}
    else {var different=new LinkedHashMap<>(body);different.put("newPassword","not allowed");deny(call("identity","edge",18081,"POST",path,bytes(different),headers),400,login);}
    assertThat(identityValue("SELECT state FROM identity.auth_reauth_grant WHERE grant_ref='"+proof.get("grantRef").asString()+"'")).isEqualTo("ISSUED");
    assertThat(identityValue("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+login.view.get("userId").asString()+"'")).isEqualTo("1");
    var forbidden=new LinkedHashMap<>(reservation);forbidden.put("proof",proof.get("proof").asString());
    deny(call("identity","learning",18441,"POST","/internal/v1/reauth-authorizations/reservations",bytes(forbidden),Map.of()),409,login);
    var response=call("identity","edge",18081,"POST",path,bytes(body),headers);String operation=json(response,202).get("operationId").asString();awaitCompleted(operation);
    deny(call("identity","learning",18441,"POST","/internal/v1/reauth-authorizations/reservations",bytes(reservation),Map.of()),401,login);
    assertThat(identityValue("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+login.view.get("userId").asString()+"'")).isEqualTo("2");
  }

  /** Receipt Learning đã commit thật bị fault peer sửa ở wire. Identity phải
   * PAUSE đúng lane/không ACK/không complete; operation độc lập vẫn tiến được.
   * Fault peer không tự là receiver durable và không được tự unpause poison. */
  @ParameterizedTest @ValueSource(strings={"participantGeneration","durable"}) @Tag("SEC-08") @Tag("DATA-15")
  void ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier(String field)throws Exception {
    Login login=login();stop("learning");String operation=null;
    try {
      var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
      operation=json(call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString())),headers),202).get("operationId").asString();
      stop("identity");start("learning");byte[] exact=command(operation);
      byte[] receipt=jsonBytes(call("learning","identity",18443,"PUT",AdmissionWire.FENCE_PATH+operation,exact,Map.of()),200);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);stop("learning");
      var changed=(tools.jackson.databind.node.ObjectNode)WireCodec.strict(receipt).deepCopy();
      if(field.equals("durable"))changed.put(field,false);else changed.put(field,UUID.randomUUID().toString());
      try(var peer=new CanonicalFenceFaultPeer(settings,directory,"learning",UUID.fromString(operation),exact,WireCodec.JSON.writeValueAsBytes(changed))) {
        start("identity");awaitDelivery(operation,"PAUSED");assertThat(peer.requests.get()).isEqualTo(1);
        assertThat(state(operation)).isEqualTo("PENDING_FENCES");assertThat(identityValue("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+operation+"' AND consumer='learning' AND ack_bytes IS NULL")).isEqualTo("1");
      }
      start("learning");Login independent=login();var independentHeaders=headers(independent);independentHeaders.put("Idempotency-Key",UUID.randomUUID().toString());
      String next=json(call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",independent.view.get("sessionContextId").asString())),independentHeaders),202).get("operationId").asString();awaitCompleted(next);
      assertThat(state(operation)).isEqualTo("PENDING_FENCES");assertThat(command(operation)).isEqualTo(exact);assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
    }finally{if(!apps.containsKey("identity"))start("identity");if(!apps.containsKey("learning"))start("learning");}
  }

  /** Cert server sai named receiver/CA bị reject trước HTTP. Sau fault fixture
   * đóng và Learning thật trở lại, durable pending retry mới được ACK/complete. */
  @ParameterizedTest @ValueSource(strings={"edge","wrong-ca"}) @Tag("SEC-20")
  void akWrongReceiverCertificateCannotCompleteBarrier(String certificate)throws Exception {
    Login login=login();stop("learning");String operation;
    try {
      var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
      operation=json(call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString())),headers),202).get("operationId").asString();
      byte[] exact=command(operation);
      String attemptsSql="SELECT attempts::text FROM identity.auth_delivery_outbox WHERE operation_id='"+operation+"' AND consumer='learning'";
      int previousAttempts=Integer.parseInt(identityValue(attemptsSql));
      try(var peer=new CanonicalFenceFaultPeer(settings,directory,certificate,UUID.fromString(operation),exact,new byte[0])) {
        long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
        // Retry lúc Learning còn down không chứng minh đã gặp cert fixture.
        // Phải thấy socket của peer này đã vào rồi đóng, attempt mới durable;
        // requests vẫn0 mới chứng minh fail-closed trước immutable HTTP body.
        while(System.nanoTime()<deadline&&(peer.accepted.get()==0||!peer.sockets.isEmpty()||Integer.parseInt(identityValue(attemptsSql))<=previousAttempts))Thread.sleep(50);
        assertThat(peer.accepted.get()).isPositive();assertThat(peer.sockets).isEmpty();
        assertThat(Integer.parseInt(identityValue(attemptsSql))).isGreaterThan(previousAttempts);
        assertThat(peer.requests.get()).isZero();assertThat(state(operation)).isEqualTo("PENDING_FENCES");assertThat(count("learning","auth_command_receipt","operation_id",operation)).isZero();
        assertThat(identityValue("SELECT count(*) FROM identity.auth_delivery_outbox WHERE operation_id='"+operation+"' AND consumer='learning' AND ack_bytes IS NULL")).isEqualTo("1");
      }
      start("learning");awaitCompleted(operation);assertThat(command(operation)).isEqualTo(exact);
    }finally{if(!apps.containsKey("learning"))start("learning");}
  }

  /** DATA15: own-Learning strict store giữ poison/gap thật, không receipt hoặc
   * cursor advance. Actual canonical worker vẫn nhận hai INT02 durable ACK rồi
   * complete. Strict payload là fixture foundation, không publisher R06c. */
  @Test @Tag("DATA-15")
  void alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream()throws Exception {
    var generations=new HashMap<String,UUID>();for(String service:SERVICES)generations.put(service,UUID.fromString(trust.get(service).getProperty("generation")));
    UUID realm=UUID.fromString(trust.get("learning").getProperty("realm")),message=UUID.randomUUID();String stream="canonical-pub-poison-"+message;
    var store=new DeliveryStore(source("learning","app"),realm,generations.get("learning"),generations);
    // Import type tránh tên package lab bị Properties lab của driver che mất.
    var template=DeliveryWire.create(message,UUID.randomUUID(),UUID.randomUUID(),"content",realm,generations.get("learning"),generations.get("content"),stream,1,"UNKNOWN_TYPE","APPLY","{}".getBytes(StandardCharsets.UTF_8),java.time.Instant.now());
    // Valid Content -> Learning metadata, bad digest: production store kiểm
    // actual own role/realm/generation rồi quarantine trước payload callback.
    var fields=new LinkedHashMap<String,Object>();WireCodec.strict(template.bytes()).properties().forEach(p->fields.put(p.getKey(),p.getValue()));
    fields.put("producer","content");fields.put("consumer","learning");fields.put("producerGeneration",generations.get("content").toString());fields.put("participantGeneration",generations.get("learning").toString());fields.put("payloadDigest","0".repeat(64));byte[] poison=bytes(fields);
    var noEffect=new DeliveryStore.Consumer(){
      // Fixture không có publication domain state; không giả business gate.
      public void guard(Connection c,DeliveryWire.Envelope e){}
      public String poison(DeliveryWire.Envelope e){throw new AssertionError("Bad digest must fail before payload adapter");}
      public void apply(Connection c,DeliveryWire.Envelope e){throw new AssertionError("Strict poison/gap cannot apply");}
    };
    assertThat(store.receive(poison,"content",noEffect).error()).isEqualTo("POISON_QUARANTINED");
    assertThat(count("learning","delivery_quarantine","message_id",message.toString())).isEqualTo(1);
    var next=new LinkedHashMap<>(fields);next.put("messageId",UUID.randomUUID().toString());next.put("deliverySequence","2");next.put("payloadDigest",sha("{}".getBytes(StandardCharsets.UTF_8)));
    assertThat(store.receive(bytes(next),"content",noEffect).error()).isEqualTo("STREAM_GAP");
    Login login=login();var headers=headers(login);headers.put("Idempotency-Key",UUID.randomUUID().toString());
    String operation=json(call("identity","edge",18081,"POST","/api/v1/auth/logout",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString())),headers),202).get("operationId").asString();awaitCompleted(operation);
    for(String service:List.of("content","learning"))assertThat(count(service,"auth_command_receipt","operation_id",operation)).isEqualTo(1);
    assertThat(count("learning","delivery_quarantine","message_id",message.toString())).isEqualTo(1);assertThat(count("learning","delivery_inbox","stream_key",stream)).isZero();
    try(var c=source("learning","app").getConnection();var s=c.prepareStatement("SELECT last_sequence,receipt_head FROM learning.delivery_receiver_stream WHERE producer='content' AND stream_key=?")){
      s.setString(1,stream);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isZero();assertThat(r.getString(2)).isEqualTo("0".repeat(64));assertThat(r.next()).isFalse();}
    }
    // Không clear/unpause strict poison để có kết quả tốt. Duplicate còn deny
    // sau canonical completion và old admission không hồi sinh ở actual hook.
    assertThat(store.receive(poison,"content",noEffect).error()).isEqualTo("POISON_QUARANTINED");assertThat(probe("learning",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
  }

  /** R03 maintenance chỉ commit credential+pending intent. Actual worker sau
   * restart materialize đúng ID/two outboxes, không consume/đổi hash lần2;
   * DISABLE/ENABLE auth ACK không thay ACCOUNT_STATE journal proof của R29b. */
  @ParameterizedTest @ValueSource(strings={"RESET","DISABLE"}) @Tag("SEC-12") @Tag("SEC-07") @Timeout(150)
  void amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate(String action)throws Exception {
    String name=account();Login login=login(name,PASSWORD);String user=login.view.get("userId").asString(),newPassword="canonical legacy reset replacement 🔐";
    stop("identity");stop("learning");
    try {
      if(action.equals("RESET")){
        String token=null;for(String line:maintenance("issue-reset",user,null).lines().toList())if(line.startsWith("token="))token=line.substring(6);
        if(token==null)throw new IllegalStateException("Owned reset token missing");assertThat(maintenance("reset","stdin",token+"\n"+newPassword+"\n").trim()).isEqualTo("VERIFIED");
      }else assertThat(maintenance("disable",user,null).trim()).isEqualTo("ACCOUNT_TRANSITION_PENDING");
      String operation=identityValue("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+user+"' ORDER BY credential_epoch DESC LIMIT 1"),fingerprint=identityValue("SELECT md5(password_hash) FROM identity.app_user WHERE user_id='"+user+"'");
      assertThat(identityValue("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("2");assertThat(count("identity","auth_operation","operation_id",operation)).isZero();start("identity");
      long materialize=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(count("identity","auth_operation","operation_id",operation)==0&&System.nanoTime()<materialize)Thread.sleep(50);assertThat(count("identity","auth_operation","operation_id",operation)).isEqualTo(1);
      // Sweep trước reconcile có thể tạo thêm SESSION EXPIRY từ old epoch;
      // Learning đang down khiến sender phải qua bounded retries của operation
      // đó trước ACCOUNT receipt. Contract không hứa ACK trong10s. Chờ fixture
      // tối đa30s nhưng vẫn buộc actual Content receipt1/Learning0/PENDING;
      // không đổi production network/retry/SQL deadline hoặc làm ACK giả.
      long partial=System.nanoTime()+Duration.ofSeconds(30).toNanos();while(count("content","auth_command_receipt","operation_id",operation)==0&&System.nanoTime()<partial)Thread.sleep(50);
      assertThat(state(operation)).isEqualTo("PENDING_FENCES");assertThat(count("content","auth_command_receipt","operation_id",operation)).isEqualTo(1);assertThat(count("learning","auth_command_receipt","operation_id",operation)).isZero();byte[] exact=command(operation);
      assertThat(identityValue("SELECT initiator_kind||':'||(initiating_cookie_digest IS NULL)::text FROM identity.auth_operation WHERE operation_id='"+operation+"'")).isEqualTo("MAINTENANCE:true");assertThat(count("identity","auth_fence_intent","operation_id",operation)).isEqualTo(2);assertThat(count("identity","auth_delivery_outbox","operation_id",operation)).isEqualTo(2);
      // Public adapter cần verified edge và một literal trusted-IP kể cả GET.
      // Giữ transport hợp lệ để 404 chứng minh MAINTENANCE không có browser
      // initiating binding, không lấy early403 từ thiếu proxy header làm proof.
      var statusHeaders=headers(login);statusHeaders.put("X-Session-Context",login.view.get("sessionContextId").asString());
      json(call("identity","edge",18081,"GET","/api/v1/auth/operations/"+operation,null,statusHeaders),404);
      stop("identity");start("identity");assertThat(state(operation)).isEqualTo("PENDING_FENCES");start("learning");awaitCompleted(operation);
      assertThat(command(operation)).isEqualTo(exact);for(String service:List.of("content","learning"))assertThat(count(service,"auth_command_receipt","operation_id",operation)).isEqualTo(1);
      // Fingerprint chỉ trong RAM; assertion boolean không in hash khi sai.
      assertThat(identityValue("SELECT md5(password_hash) FROM identity.app_user WHERE user_id='"+user+"'").equals(fingerprint)).isTrue();assertThat(identityValue("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("2");
      assertThat(probe("content",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");assertThat(probe("learning",login,UUID.randomUUID()).get("state").asString()).isEqualTo("DENIED");
      if(action.equals("RESET")){
        assertThat(identityValue("SELECT count(*) FROM identity.credential_token WHERE user_id='"+user+"' AND purpose='RESET' AND consumed_at IS NOT NULL")).isEqualTo("1");assertThat(identityValue("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("false");Login fresh=login(name,newPassword);assertThat(fresh.view.get("credentialEpoch").asString()).isEqualTo("2");
      }else {
        assertThat(identityValue("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("true");assertThat(identityValue("SELECT state FROM identity.credential_transition WHERE operation_id='"+operation+"'")).isEqualTo("PENDING");
        assertThat(maintenance("enable",user,null).trim()).isEqualTo("ACCOUNT_TRANSITION_PENDING");String enabled=identityValue("SELECT operation_id::text FROM identity.credential_transition WHERE user_id='"+user+"' ORDER BY credential_epoch DESC LIMIT 1");
        long ready=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(count("identity","auth_operation","operation_id",enabled)==0&&System.nanoTime()<ready)Thread.sleep(50);awaitCompleted(enabled);
        assertThat(identityValue("SELECT credential_epoch::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("3");assertThat(identityValue("SELECT auth_transition_pending::text FROM identity.app_user WHERE user_id='"+user+"'")).isEqualTo("true");assertThat(identityValue("SELECT count(*) FROM identity.credential_transition WHERE user_id='"+user+"' AND state='PENDING' AND action IN ('DISABLED','ACTIVE')")).isEqualTo("2");
      }
    }finally{if(!apps.containsKey("learning"))start("learning");if(!apps.containsKey("identity"))start("identity");}
  }

  /** Edge thật trong browser: natural Origin, HttpOnly cookie application và
   * multi-tab không được thay bằng Java HTTP headers. Driver dừng Learning để
   * độc lập kiểm pending/partial ACK; sau đó khôi phục actual receiver và kiểm
   * hai durable receipts trước cho browser đọc status/consumed retry. */
  @ParameterizedTest @ValueSource(strings={"LOGOUT_ALL","PASSWORD_CHANGE"})
  @Tag("SEC-19") @Tag("SEC-23") @Tag("SEC-11") @Timeout(150)
  void azActualSensitiveBrowserCookieReorderAndMultiTab(String purpose)throws Exception {
    closed(17441);String a=account(),b=account();stop("learning");
    Path script=root.resolve("scripts/tests/canonical_session_browser.mjs");assertThat(script).isRegularFile();
    Path node=Path.of(System.getProperty("user.home"),".cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe");assertThat(node).isRegularFile();
    var builder=new ProcessBuilder(node.toString(),script.toString()).directory(root.toFile())
        .redirectError(directory.resolve("canonical-browser-"+purpose+"-stderr.log").toFile());sanitize(builder);
    Process browser=builder.start();var readerPool=Executors.newSingleThreadExecutor();
    var reader=new BufferedReader(new InputStreamReader(browser.getInputStream(),StandardCharsets.UTF_8));
    try {
      var input=browser.getOutputStream();input.write(bytes(Map.of("origin",ORIGIN,"directory",directory.toString(),"storePassword",settings.storePassword(),"accountA",a,"accountB",b,"password",PASSWORD,"purpose",purpose)));input.write('\n');input.flush();
      JsonNode pending=browserStage(reader,readerPool,purpose,"PENDING");
      String operation=WireCodec.idempotencyKey(pending.get("operationId").asString()),contextA=WireCodec.idempotencyKey(pending.get("contextA").asString());
      assertThat(state(operation)).isEqualTo("PENDING_FENCES");
      long receiptDeadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
      while(count("content","auth_command_receipt","operation_id",operation)!=1&&System.nanoTime()<receiptDeadline)Thread.sleep(25);
      assertThat(count("content","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isZero();
      String idleASql="SELECT s.last_interactive_at::text FROM identity.auth_session s JOIN identity.auth_operation o ON o.initiating_context_id=s.context_id WHERE o.operation_id='"+operation+"'",idleA=identityValue(idleASql);
      input.write(bytes(Map.of("state","PENDING_FENCES")));input.write('\n');input.flush();
      JsonNode completion=browserStage(reader,readerPool,purpose,"COMPLETE");
      assertThat(completion.get("operationId").asString()).isEqualTo(operation);assertThat(completion.get("contextA").asString()).isEqualTo(contextA);
      assertThat(identityValue(idleASql)).isEqualTo(idleA);start("learning");awaitCompleted(operation);
      assertThat(count("learning","auth_command_receipt","operation_id",operation)).isEqualTo(1);
      input.write(bytes(Map.of("state","COMPLETED")));input.write('\n');input.flush();
      JsonNode activeB=browserStage(reader,readerPool,purpose,"B_ACTIVE");
      assertThat(activeB.get("operationId").asString()).isEqualTo(operation);
      String contextB=WireCodec.idempotencyKey(activeB.get("contextB").asString());
      String idleSql="SELECT last_interactive_at::text FROM identity.auth_session WHERE context_id='"+contextB+"'",idleB=identityValue(idleSql);
      input.write(bytes(Map.of("state","B_ACTIVE")));input.write('\n');input.flush();
      JsonNode result=browserStage(reader,readerPool,purpose,"RESULT");assertThat(result.get("status").asString()).isEqualTo("PASS");
      assertThat(result.get("purpose").asString()).isEqualTo(purpose);assertThat(result.get("origin").asString()).isEqualTo(ORIGIN);
      Set<String> labels=new HashSet<>();result.get("cases").forEach(value->labels.add(value.asString()));
      assertThat(labels).contains("natural-origin-sensitive-proof","pending-without-cookie","late-A-no-cookie-B-survives","cross-cookie-retry-denied","cross-cookie-status-denied","initiating-status-completed-only","original-bound-sensitive-retry-completed","foreign-origin-denied","no-status-proof-sensitive-cookie-emission","no-browser-runtime-errors");
      assertThat(result.get("checks").asInt()).isEqualTo(labels.size());assertThat(labels.size()).isGreaterThanOrEqualTo(20);
      input.close();assertThat(browser.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(browser.exitValue()).isZero();closed(17441);
      // Browser assertions một mình không chứng minh SQL idle/epoch/no-repeat.
      // So snapshot riêng sau cả poll/denied retries và bound consumed retry.
      assertThat(identityValue(idleSql)).isEqualTo(idleB);assertThat(identityValue(idleASql)).isEqualTo(idleA);
      assertThat(identityValue("SELECT count(*) FROM identity.auth_reauth_grant WHERE consumed_operation_ref='"+operation+"' AND state='CONSUMED'")).isEqualTo("1");
      assertThat(identityValue("SELECT u.credential_epoch::text FROM identity.app_user u JOIN identity.auth_operation o ON o.target_user_id=u.user_id WHERE o.operation_id='"+operation+"'")).isEqualTo("2");
      Files.write(directory.resolve("canonical-browser-"+purpose+".json"),WireCodec.JSON.writeValueAsBytes(result));
      System.out.println("BROWSER_CANONICAL "+purpose+" actual Chromium checks="+labels.size());
    }finally {
      // Đóng control input trước để Node có thể chạy finally/browser.close().
      // Nếu vẫn treo, chụp đúng descendants của own child trước khi dừng parent;
      // không tìm/kill toàn bộ Edge hoặc process theo tên trên máy người dùng.
      browser.getOutputStream().close();
      if(browser.isAlive()&&!browser.waitFor(5,TimeUnit.SECONDS)) {
        var descendants=browser.toHandle().descendants().toList();browser.destroyForcibly();
        for(var child:descendants)if(child.isAlive())child.destroyForcibly();
        assertThat(browser.waitFor(5,TimeUnit.SECONDS)).isTrue();
        for(var child:descendants)child.onExit().get(5,TimeUnit.SECONDS);
      }
      // Kill/EOF phải trước close(reader): một readLine đang block giữ monitor,
      // đóng reader trước có thể chặn cleanup và không bao giờ tới kill child.
      reader.close();
      readerPool.shutdownNow();assertThat(readerPool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();closed(17441);
      if(!apps.containsKey("learning"))start("learning");
    }
  }

  /** Giữ diagnostics của browser FAIL trước assertion. Chỉ copy phase thuộc
   * allowlist/count, không raw Node output hoặc exception có thể chứa secret. */
  static JsonNode browserStage(BufferedReader reader,ExecutorService pool,String purpose,String expected)throws Exception {
    JsonNode value=browserLine(reader,pool);
    if(value.has("stage")&&value.get("stage").asString().equals("RESULT")&&value.has("status")&&value.get("status").asString().equals("FAIL")) {
      String phase=value.has("case")?value.get("case").asString():"unknown";
      if(!Set.of("setup","login-a","proof","sensitive","pending","login-b","reorder","status").contains(phase))phase="unknown";
      int checks=value.has("completedChecks")?value.get("completedChecks").asInt():-1;
      if(checks<0||checks>23)checks=-1;
      Files.write(directory.resolve("canonical-browser-"+purpose+"-failure.json"),bytes(Map.of("stage","RESULT","status","FAIL","case",phase,"completedChecks",checks)));
      throw new AssertionError("Browser FAIL phase="+phase+"; completedChecks="+checks);
    }
    assertThat(value.get("stage").asString()).isEqualTo(expected);return value;
  }

  /** Bounded đọc Node control protocol; không ghi raw line có thể chứa secret
   * khi input/output sai, không block toàn qualification ở readLine(). */
  static JsonNode browserLine(BufferedReader reader,ExecutorService pool)throws Exception {
    String line=pool.submit(()->reader.readLine()).get(30,TimeUnit.SECONDS);
    if(line==null||line.length()>8192)throw new IllegalStateException("Browser control unavailable");
    return WireCodec.strict(line.getBytes(StandardCharsets.UTF_8));
  }

  static Map<String,Object> admissionRequest(Login login,boolean mutation)throws Exception {
    String method=mutation?"PUT":"GET",path=mutation?"/api/v1/drafts/"+UUID.randomUUID():"/api/v1/topics/canonical-topic/units/canonical-unit";
    String body=sha(mutation?"{}".getBytes(StandardCharsets.UTF_8):new byte[0]);
    var request=new LinkedHashMap<String,Object>(Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("action",mutation?"LEARNING_DRAFT_SAVE":"CONTENT_BODY_READ"),Map.entry("audience",mutation?"learning":"content"),Map.entry("requestNonce",UUID.randomUUID().toString()),Map.entry("sessionCredential",login.cookie),Map.entry("expectedContextId",login.view.get("sessionContextId").asString()),Map.entry("method",method),Map.entry("canonicalPath",path),Map.entry("query",""),Map.entry("bodySha256",body),Map.entry("requestBinding",sha((method+"\n"+path+"\n\n"+body).getBytes(StandardCharsets.UTF_8)))));
    if(mutation){request.put("csrfToken",login.view.get("csrfToken").asString());request.put("origin",ORIGIN);}return request;
  }
  static JsonNode mint(Login login,String key,String purpose,String intent)throws Exception {
    var headers=headers(login);headers.put("Idempotency-Key",key);
    var response=call("identity","edge",18081,"POST","/api/v1/auth/reauth",bytes(Map.of("schemaVersion",1,"expectedContextId",login.view.get("sessionContextId").asString(),"password",PASSWORD,"purpose",purpose,"intentHash",intent)),headers);
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();return json(response,200);
  }
  static Map<String,Object> reservationRequest(Login login,String proof,String key,String intent)throws Exception {
    String session=identityValue("SELECT session_ref::text FROM identity.auth_session WHERE context_id='"+login.view.get("sessionContextId").asString()+"'");
    return new LinkedHashMap<>(Map.of("schemaVersion",1,"proof",proof,"userId",login.view.get("userId").asString(),"sessionRef",session,"sessionContextId",login.view.get("sessionContextId").asString(),"credentialEpoch",login.view.get("credentialEpoch").asString(),"authRealmId",trust.get("identity").getProperty("realm"),"purpose","LEARNING_RESET","operationKey",key,"intentHash",intent));
  }
  static void deny(HttpResponse<byte[]> response,int expected,Login login){
    json(response,expected);assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(new String(response.body(),StandardCharsets.UTF_8)).doesNotContain(PASSWORD,login.cookie,login.view.get("csrfToken").asString(),"SQLException");
  }
  static byte[] jsonBytes(HttpResponse<byte[]> response,int expected){json(response,expected);return response.body();}
  static String sha(byte[] value)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}
  /** SQL scalar chỉ dùng constants/UUID từ fixture server, không HTTP input. */
  static String identityValue(String sql)throws Exception {try(var c=source("identity","app").getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new IllegalStateException("Fixture row missing");return r.getString(1);}}
  static void fixtureSql(String service,String sql)throws Exception {try(var c=source(service,"migrator").getConnection();var s=c.createStatement()){s.execute(sql);}}
  static void awaitDelivery(String operation,String status)throws Exception {long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();while(System.nanoTime()<deadline){if(identityValue("SELECT delivery_status FROM identity.auth_delivery_outbox WHERE operation_id='"+operation+"' AND consumer='learning'").equals(status))return;Thread.sleep(50);}throw new IllegalStateException("Delivery status not observed");}
  static String expiredProofFixture(String template)throws Exception {
    byte[] entropy=new byte[32];new java.security.SecureRandom().nextBytes(entropy);String proof=Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    UUID reference=UUID.randomUUID();
    try(var c=source("identity","migrator").getConnection();var s=c.prepareStatement("WITH stamp AS (SELECT clock_timestamp() AS at) INSERT INTO identity.auth_reauth_grant(proof_digest,grant_ref,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,issued_at,expires_at) SELECT ?,?,user_id,session_ref,context_id,credential_epoch,realm_id,purpose,operation_key,intent_digest,stamp.at,stamp.at+interval '2 seconds' FROM identity.auth_reauth_grant CROSS JOIN stamp WHERE grant_ref=?")) {
      // Store digest của opaque ASCII handle, không decoded entropy; kiểm row
      // tồn tại + expired bên caller để401 không chỉ là unknown-proof denial.
      // Insert guard phải chấp nhận grant còn hạn, các cap/FK/TTL guards giữ
      // nguyên. Chỉ fixture được cấp TTL ngắn hợp lệ, không public mint API.
      s.setBytes(1,java.security.MessageDigest.getInstance("SHA-256").digest(proof.getBytes(StandardCharsets.US_ASCII)));s.setObject(2,reference);s.setObject(3,UUID.fromString(template));assertThat(s.executeUpdate()).isEqualTo(1);
    }
    long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();
    String expiredSql="SELECT (expires_at<=clock_timestamp())::text FROM identity.auth_reauth_grant WHERE grant_ref='"+reference+"'";
    while(!identityValue(expiredSql).equals("true")&&System.nanoTime()<deadline)Thread.sleep(25);
    assertThat(identityValue(expiredSql)).isEqualTo("true");return proof;
  }
}
