package lab.backend.learning.contentreplica;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import lab.backend.learning.transport.TransportPeerClient;
import lab.backend.learning.transport.TransportSettings;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Hai packaged JAR + own PostgreSQL/mTLS chứng minh INT-03 consumer và INT-04 provider. */
@Timeout(90)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicationRuntimeIT {
  private static final UUID OP=UUID.fromString("10000000-0000-4000-8000-000000000001");
  private static final Properties DATABASE=new Properties(),CONTENT=new Properties(),LEARNING=new Properties();
  private static final byte[] MANIFEST="[{\"capabilityId\":\"CAP-COL-002-A\",\"version\":\"1\",\"newAttemptAllowed\":true,\"submitAllowed\":true,\"finalizeAllowed\":true,\"currentEvidencePolicy\":\"RETAIN\",\"bodyAvailable\":true,\"reason\":\"Typographic correction only\"}]".getBytes(StandardCharsets.UTF_8);
  private static Path directory,contentLog,learningLog;private static byte[] bundle;private static String bundleDigest,manifestDigest;private static Process content,learning;private static TransportSettings settings;

  @BeforeAll static void startPackagedPeers()throws Exception{
    String db=System.getProperty("lab.config"),trust=System.getProperty("trust.config");
    if(db==null||trust==null||db.startsWith("${")||trust.startsWith("${"))throw new IllegalStateException("Managed R06b PostgreSQL/mTLS required");
    try(Reader r=Files.newBufferedReader(Path.of(db).toRealPath())){DATABASE.load(r);}
    Path learningConfig=Path.of(trust).toRealPath();directory=learningConfig.getParent();Path contentConfig=directory.resolve("content.properties").toRealPath();
    load(contentConfig,CONTENT);load(learningConfig,LEARNING);
    // Topology cố định cho R06b giữ Content R06a ở 8080/8443 và đặt Learning ở 18083/18443.
    LEARNING.setProperty("schemaVersion","2");LEARNING.setProperty("listenerTopology",TransportSettings.PUBLICATION);store(learningConfig,LEARNING);
    migrate("learning",source("learning","migrator"),Path.of("src/main/resources/db/migration").toRealPath());
    migrate("content",source("content","migrator"),Path.of("../content-service/src/main/resources/db/migration").toRealPath());
    bundle=Files.readAllBytes(Path.of("../../../contracts/fixtures/publication/fx05-private-bundle.json").toRealPath());bundleDigest=PublicationWire.sha256(bundle);manifestDigest=PublicationWire.sha256(MANIFEST);
    seed();
    var env=new StandardEnvironment();env.setActiveProfiles("publication");env.getPropertySources().addFirst(new MapPropertySource("fixture",Map.of("backendlab.trust.config",learningConfig.toString())));settings=TransportSettings.load(env);
    contentLog=directory.resolve("content-publication-r06b.log");learningLog=directory.resolve("learning-publication-r06b.log");
    content=start("content","publication",contentConfig,contentLog);await(content,8443,"content");
    learning=start("learning","publication",learningConfig,learningLog);await(learning,18443,"learning");
  }

  @AfterAll static void stopAndAudit()throws Exception{
    stop(learning);stop(content);
    for(int port:new int[]{8080,8443,18083,18443})assertThat(closed(port)).as("owned listener closed "+port).isTrue();
    for(Path log:List.of(contentLog,learningLog))if(log!=null&&Files.exists(log))assertThat(Files.readString(log)).doesNotContain("PRIVATE_EXPECTED_MUST_NOT_REACH_PUBLIC_OUTLINE","SQLException","password");
  }

  @Test @Order(1) void packagedStageFenceReleaseStatusAndLostAckRetriesConverge()throws Exception{
    // Future gap đi qua packaged INT-03 pull nhưng phải bị cursor chặn trước mọi Learning effect.
    UUID gap=UUID.randomUUID();assertThat(call("content",18443,path(gap,"STAGE"),"PUT",command(gap,"STAGE",2,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(409);assertThat(number("learning","app","SELECT count(*) FROM learning.publication_operation")).isZero();assertThat(number("learning","app","SELECT last_sequence FROM learning.publication_delivery_cursor WHERE stream_key='content-global'")).isZero();
    UUID aborted=UUID.fromString("10000000-0000-4000-8000-000000000002");assertThat(call("content",18443,path(aborted,"STAGE"),"PUT",command(aborted,"STAGE",1,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);assertThat(call("content",18443,path(aborted,"FENCE"),"PUT",command(aborted,"FENCE",2,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);byte[] abort=command(aborted,"ABORT",3,1,2,"fx05-p2",bundleDigest,manifestDigest);var abortReceipt=call("content",18443,path(aborted,"ABORT"),"PUT",abort,Map.of("Content-Type","application/json"));assertThat(abortReceipt.statusCode()).isEqualTo(200);assertThat(call("content",18443,path(aborted,"ABORT"),"PUT",abort,Map.of("Content-Type","application/json")).body()).containsExactly(abortReceipt.body());assertThat(number("learning","app","SELECT applied_revision FROM learning.publication_head WHERE singleton")).isEqualTo(1);assertThat(number("learning","app","SELECT count(*) FROM learning.publication_head WHERE fence_operation IS NOT NULL")).isZero();
    byte[] stage=command(OP,"STAGE",4,1,2,"fx05-p2",bundleDigest,manifestDigest);var first=call("content",18443,path(OP,"STAGE"),"PUT",stage,Map.of("Content-Type","application/json"));assertThat(first.statusCode()).isEqualTo(200);assertThat(call("content",18443,path(OP,"STAGE"),"PUT",stage,Map.of("Content-Type","application/json")).body()).containsExactly(first.body());
    byte[] fence=command(OP,"FENCE",5,1,2,"fx05-p2",bundleDigest,manifestDigest);var fenced=call("content",18443,path(OP,"FENCE"),"PUT",fence,Map.of("Content-Type","application/json"));assertThat(fenced.statusCode()).isEqualTo(200);stop(learning);learning=start("learning","publication",directory.resolve("learning.properties"),learningLog);await(learning,18443,"learning");assertThat(call("content",18443,path(OP,"FENCE"),"PUT",fence,Map.of("Content-Type","application/json")).body()).containsExactly(fenced.body());
    byte[] release=command(OP,"RELEASE",6,1,2,"fx05-p2",bundleDigest,manifestDigest);assertThat(call("content",18443,path(OP,"RELEASE"),"PUT",release,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);var status=call("content",18443,"/internal/v1/publication-operations/"+OP,"GET",null,Map.of());assertThat(status.statusCode()).isEqualTo(200);assertThat(new String(status.body(),StandardCharsets.UTF_8)).contains("\"state\":\"RELEASED\"").doesNotContain("PRIVATE_EXPECTED","expected");
    byte[] staleBundle=new String(bundle,StandardCharsets.UTF_8).replace("PRIVATE_EXPECTED_MUST_NOT_REACH_PUBLIC_OUTLINE","PRIVATE_EXPECTED_STALE_SEQUENCE").getBytes(StandardCharsets.UTF_8);String staleDigest=PublicationWire.sha256(staleBundle);insertContent("fx05-p3",3,staleBundle,MANIFEST,true);UUID stale=UUID.randomUUID();assertThat(call("content",18443,path(stale,"STAGE"),"PUT",command(stale,"STAGE",6,2,3,"fx05-p3",staleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(409);
    assertThat(number("learning","app","SELECT last_sequence FROM learning.publication_delivery_cursor WHERE stream_key='content-global'")).isEqualTo(6);assertThat(number("learning","app","SELECT applied_revision FROM learning.publication_head WHERE singleton")).isEqualTo(2);assertThat(number("learning","app","SELECT count(*) FROM learning.content_replica")).isEqualTo(1);assertThat(number("learning","app","SELECT count(*) FROM learning.publication_operation")).isEqualTo(2);
  }

  @Test @Order(2) void privateProviderRejectsPublicWrongCallerHeadersRoutesAndCertificatesBeforeEffect()throws Exception{
    byte[] body=command(UUID.randomUUID(),"STAGE",10,2,3,"missing",bundleDigest,manifestDigest);long before=number("learning","app","SELECT count(*) FROM learning.publication_operation");
    assertThat(call("edge",18083,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(404);
    for(String caller:List.of("identity","learning","operator"))assertThat(call(caller,18443,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(403);
    assertThat(call("content",18443,path(readOperation(body),"STAGE")+"?x=1","PUT",body,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(400);
    assertThat(call("content",18443,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json","Origin","https://attacker.invalid")).statusCode()).isEqualTo(403);
    assertThat(call("content",18443,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json","X-Caller","content")).statusCode()).isEqualTo(403);
    assertThat(call("content",18443,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json","Cookie","session=forged")).statusCode()).isEqualTo(403);
    assertThat(call("content",18443,path(readOperation(body),"STAGE"),"POST",body,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(405);
    assertThat(call("content",18443,path(readOperation(body),"STAGE"),"PUT",new byte[PublicationWire.MAX_BYTES+1],Map.of("Content-Type","application/json")).statusCode()).isEqualTo(400);
    assertThat(call("content",18443,path(readOperation(body),"STAGE")+"/","PUT",body,Map.of("Content-Type","application/json")).statusCode()).isEqualTo(404);
    for(String caller:List.of("wrong-ca","wrong-eku","wrong-san","wrong-env","ambiguous"))cannotReach(caller,body);
    assertThat(number("learning","app","SELECT count(*) FROM learning.publication_operation")).isEqualTo(before);
  }

  @Test @Order(3) void invalidPrivateBundleAndContentOutageLeaveLastGoodReplicaUntouched()throws Exception{
    if(number("learning","app","SELECT count(*) FROM learning.content_replica")==0){UUID good=UUID.randomUUID();assertThat(call("content",18443,path(good,"STAGE"),"PUT",command(good,"STAGE",1,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);assertThat(call("content",18443,path(good,"FENCE"),"PUT",command(good,"FENCE",2,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);assertThat(call("content",18443,path(good,"RELEASE"),"PUT",command(good,"RELEASE",3,1,2,"fx05-p2",bundleDigest,manifestDigest),Map.of("Content-Type","application/json")).statusCode()).isEqualTo(200);}
    long replicas=number("learning","app","SELECT count(*) FROM learning.content_replica"),operations=number("learning","app","SELECT count(*) FROM learning.publication_operation"),head=number("learning","app","SELECT applied_revision FROM learning.publication_head WHERE singleton");
    byte[] invalid="{\"schemaVersion\":2,\"id\":\"PRIVATE_INVALID\"}".getBytes(StandardCharsets.UTF_8);String invalidDigest=PublicationWire.sha256(invalid);insertContent("fx05-invalid",3,invalid,MANIFEST,true);UUID invalidOp=UUID.randomUUID();var response=call("content",18443,path(invalidOp,"STAGE"),"PUT",command(invalidOp,"STAGE",4,2,3,"fx05-invalid",invalidDigest,manifestDigest),Map.of("Content-Type","application/json"));assertThat(response.statusCode()).isEqualTo(409);assertThat(new String(response.body(),StandardCharsets.UTF_8)).doesNotContain("PRIVATE_INVALID","expected","SQLException");
    stop(content);content=null;UUID unavailable=UUID.randomUUID();response=call("content",18443,path(unavailable,"STAGE"),"PUT",command(unavailable,"STAGE",5,2,3,"missing",bundleDigest,manifestDigest),Map.of("Content-Type","application/json"));assertThat(response.statusCode()).isEqualTo(503);
    assertThat(number("learning","app","SELECT count(*) FROM learning.content_replica")).isEqualTo(replicas);assertThat(number("learning","app","SELECT count(*) FROM learning.publication_operation")).isEqualTo(operations);assertThat(number("learning","app","SELECT applied_revision FROM learning.publication_head WHERE singleton")).isEqualTo(head);
  }

  private static void seed()throws Exception{
    try(Connection c=source("content","migrator").getConnection();var s=c.createStatement()){s.executeUpdate("INSERT INTO content.publication_canonical_head(revision,bundle_id,bundle_digest) VALUES(1,'fx05-p1',repeat('0',64))");}
    insertContent("fx05-p2",2,bundle,MANIFEST,true);
    try(Connection c=source("learning","migrator").getConnection();var s=c.createStatement()){s.executeUpdate("INSERT INTO learning.publication_environment VALUES(true,'"+LEARNING.getProperty("realm")+"','"+LEARNING.getProperty("generation")+"','"+CONTENT.getProperty("generation")+"',true)");s.executeUpdate("INSERT INTO learning.publication_head(singleton,applied_revision) VALUES(true,1)");s.executeUpdate("INSERT INTO learning.publication_delivery_cursor(stream_key,last_sequence) VALUES('content-global',0)");}
  }
  private static void insertContent(String id,long revision,byte[] bytes,byte[] manifest,boolean valid)throws Exception{try(Connection c=source("content","migrator").getConnection();var s=c.prepareStatement("INSERT INTO content.publication_bundle(bundle_id,target_revision,bundle_bytes,bundle_digest,manifest_bytes,manifest_digest,validated) VALUES(?,?,?,?,?,?,?)")){s.setString(1,id);s.setLong(2,revision);s.setBytes(3,bytes);s.setString(4,PublicationWire.sha256(bytes));s.setBytes(5,manifest);s.setString(6,PublicationWire.sha256(manifest));s.setBoolean(7,valid);s.executeUpdate();}}
  private static void migrate(String service,PGSimpleDataSource ds,Path path){Flyway.configure().dataSource(ds).defaultSchema("migration_metadata").schemas("migration_metadata").createSchemas(false).cleanDisabled(true).locations("filesystem:"+path.toString().replace('\\','/')).load().migrate();}
  private static PGSimpleDataSource source(String service,String kind){var ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{Integer.parseInt(DATABASE.getProperty("lab.port"))});ds.setDatabaseName(service+"_db");ds.setUser(DATABASE.getProperty(service+"."+kind+".user"));ds.setPassword(DATABASE.getProperty(service+"."+kind+".password"));ds.setSslMode("disable");return ds;}
  private static Process start(String service,String profile,Path config,Path log)throws Exception{Path jar=Path.of("../"+service+"-service/target/"+service+"-service-0.1.0-SNAPSHOT.jar").toRealPath();var b=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-jar",jar.toString(),"--spring.profiles.active="+profile,"--backendlab.trust.config="+config,"--logging.level.root=ERROR");b.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());b.environment().keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("SERVER_")||k.startsWith("MANAGEMENT_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("_JAVA_OPTIONS"));return b.start();}
  private static void await(Process process,int port,String service)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(30).toNanos();while(System.nanoTime()<end){if(!process.isAlive())throw new IllegalStateException(service+" packaged process exited");try{if(call(service.equals("content")?"learning":"content",port,"/internal/v1/transport/probes","POST","{}".getBytes(StandardCharsets.UTF_8),Map.of("Content-Type","application/json")).statusCode()>0)return;}catch(Exception ignored){}Thread.sleep(100);}throw new IllegalStateException(service+" packaged process not ready");}
  private static HttpResponse<byte[]> call(String identity,int port,String path,String method,byte[] body,Map<String,String> headers)throws Exception{Path key=directory.resolve(identity+".p12");SSLContext ssl=TransportPeerClient.tls(settings,key,port==8443?"content":"learning");try(HttpClient client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(2)).build()){var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5));headers.forEach(request::header);return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());}}
  private static void cannotReach(String identity,byte[] body){try{var r=call(identity,18443,path(readOperation(body),"STAGE"),"PUT",body,Map.of("Content-Type","application/json"));assertThat(r.statusCode()).isNotEqualTo(200);assertThat(new String(r.body(),StandardCharsets.UTF_8)).doesNotContain("PRIVATE_EXPECTED","expected");}catch(java.io.IOException expected){}catch(Exception e){throw new AssertionError(e);}}
  private static byte[] command(UUID op,String phase,long sequence,long parent,long target,String id,String bd,String md)throws Exception{return PublicationWire.JSON.writeValueAsBytes(Map.ofEntries(Map.entry("contractVersion",1),Map.entry("operationId",op.toString()),Map.entry("phase",phase),Map.entry("parentRevision",parent),Map.entry("targetRevision",target),Map.entry("bundleId",id),Map.entry("bundleSha256",bd),Map.entry("manifestSha256",md),Map.entry("realmId",LEARNING.getProperty("realm")),Map.entry("producerGeneration",CONTENT.getProperty("generation")),Map.entry("participantGeneration",LEARNING.getProperty("generation")),Map.entry("deliverySequence",sequence)));}
  private static String path(UUID op,String phase){return "/internal/v1/publication-operations/"+op+"/phases/"+phase;}
  private static UUID readOperation(byte[] body){try{return UUID.fromString(PublicationWire.JSON.readTree(body).path("operationId").asString());}catch(Exception e){throw new IllegalArgumentException(e);}}
  private static long number(String service,String kind,String sql)throws Exception{try(Connection c=source(service,kind).getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
  private static void load(Path path,Properties p)throws Exception{try(Reader r=Files.newBufferedReader(path)){p.load(r);}}
  private static void store(Path path,Properties p)throws Exception{try(var w=Files.newBufferedWriter(path)){p.store(w,null);}}
  private static void stop(Process p)throws Exception{if(p==null)return;p.destroy();if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();assertThat(p.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  private static boolean closed(int port)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();while(System.nanoTime()<end){try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),200);}catch(java.io.IOException closed){return true;}Thread.sleep(50);}return false;}
}
