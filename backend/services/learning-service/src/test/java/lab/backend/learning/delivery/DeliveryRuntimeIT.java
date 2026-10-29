package lab.backend.learning.delivery;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import lab.backend.learning.transport.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.*;

/** FX04 three own DB roles + cross-artifact producer CLI and packaged private receiver.
 * Test driver provisions lab fixtures; no runtime service holds another DB credential. */
@TestMethodOrder(MethodOrderer.MethodName.class) @Timeout(90)
public class DeliveryRuntimeIT {
  static final String SERVICE="learning",PRODUCER="content";
  static final Properties lab=new Properties();
  static final Map<String,Properties> trust=new HashMap<>();
  static final Map<String,UUID> generations=new HashMap<>();
  static Path root,directory,trustFile,config,artifact,log;
  static TransportSettings settings;
  static UUID realm;
  static Process process;
  @BeforeAll static void setup()throws Exception{
    String l=System.getProperty("lab.config"),t=System.getProperty("trust.config");if(l==null||t==null||l.startsWith("$"+"{")||t.startsWith("$"+"{"))throw new IllegalStateException("Managed actual PG/mTLS required");
    try(var reader=Files.newBufferedReader(Path.of(l))){lab.load(reader);}trustFile=Path.of(t).toRealPath();directory=trustFile.getParent();
    root=Path.of(WireCodec.JSON.readTree(Files.readAllBytes(directory.resolve("ownership.json"))).get("workspace").asString()).toRealPath();
    for(String service:List.of("identity","content","learning")){
      Properties p=new Properties();try(var reader=Files.newBufferedReader(directory.resolve(service+".properties"))){p.load(reader);}trust.put(service,p);generations.put(service,UUID.fromString(p.getProperty("generation")));
    }
    realm=UUID.fromString(trust.get(SERVICE).getProperty("realm"));
    for(String service:List.of("identity","content","learning")){
      var migration=source(service,"migrator");
      Path selected=root.resolve("backend/services/"+service+"-service/src");
      Flyway.configure().dataSource(migration).schemas("migration_metadata").defaultSchema("migration_metadata").createSchemas(false).cleanDisabled(true)
        .locations("filesystem:"+selected.resolve("main/resources/db/migration"),"filesystem:"+selected.resolve("test/resources/db-test-migration")).load().migrate();
      try(var c=migration.getConnection()){
        try(var s=c.prepareStatement("SELECT set_config('backend_lab.provision_realm',?,false),set_config('backend_lab.provision_generation',?,false),set_config('backend_lab.provision_run_id',?,false),set_config('backend_lab.provision_identity_generation',?,false),set_config('backend_lab.provision_content_generation',?,false),set_config('backend_lab.provision_learning_generation',?,false)")){
          s.setString(1,realm.toString());s.setString(2,generations.get(service).toString());s.setString(3,lab.getProperty("lab.runId"));s.setString(4,generations.get("identity").toString());s.setString(5,generations.get("content").toString());s.setString(6,generations.get("learning").toString());s.execute();
        }
        try(var s=c.createStatement()){
          s.execute(Files.readString(root.resolve("ops/"+service+"/provision-"+(service.equals("identity")?"session":"admission")+"-realm.sql")));
          s.execute(Files.readString(root.resolve("ops/"+service+"/provision-delivery-realm.sql")));s.execute(Files.readString(root.resolve("ops/"+service+"/provision-delivery-realm.sql")));
        }
      }
      Properties p=new Properties();p.putAll(Map.of("schemaVersion","1","enabled","true","adapter","LAB_APPLY","realm",realm.toString(),"generation",generations.get(service).toString(),"dbRunId",lab.getProperty("lab.runId"),"identityGeneration",generations.get("identity").toString(),"contentGeneration",generations.get("content").toString(),"learningGeneration",generations.get("learning").toString()));
      try(var writer=Files.newBufferedWriter(directory.resolve(service+"-delivery.properties"))){p.store(writer,"owned lab");}
      Path jar=jar(service);assertThat(jar).isRegularFile();
      try(var archive=new java.util.zip.ZipFile(jar.toFile())){
        assertThat(archive.getEntry("BOOT-INF/classes/lab/backend/"+service+"/delivery/DeliveryLabCli.class")).as("Fresh producer artifact required, not an older qualified sibling JAR").isNotNull();
        assertThat(archive.getEntry("BOOT-INF/classes/lab/backend/"+service+"/delivery/DeliveryRecoveryFixtureMain.class")).as("Privileged maintenance fixture must remain test-only").isNull();
      }
    }
    config=directory.resolve(SERVICE+"-delivery.properties");artifact=jar(SERVICE);log=directory.resolve(SERVICE+"-delivery-runtime.log");
    settings=TransportSettings.load(environment());DeliveryRuntime.load(environment(),settings);start();
  }
  @AfterAll static void cleanup()throws Exception{stop();for(int port:new int[]{8080,8443})closed(port);}
  static Path jar(String service){return root.resolve("backend/services/"+service+"-service/target/"+service+"-service-0.1.0-SNAPSHOT.jar");}
  static StandardEnvironment environment(){var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("fixture",Map.of("backendlab.trust.config",trustFile.toString(),"backendlab.delivery.config",config.toString())));return env;}
  static PGSimpleDataSource source(String service,String kind){var s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(lab.getProperty("lab.port"))});s.setDatabaseName(service+"_db");s.setUser(lab.getProperty(service+"."+kind+".user"));s.setPassword(lab.getProperty(service+"."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(8);s.setSslMode("disable");return s;}
  static void start()throws Exception{
    for(int port:new int[]{8080,8443})closed(port);
    var b=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-jar",artifact.toString(),"--spring.profiles.active=delivery","--backendlab.trust.config="+trustFile,"--backendlab.delivery.config="+config,"--logging.level.root=ERROR");
    b.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));sanitize(b);process=b.start();
    long end=System.nanoTime()+Duration.ofSeconds(35).toNanos();while(System.nanoTime()<end){if(!process.isAlive())throw new IllegalStateException("Delivery receiver exited; retained log");try{if(call("edge",8080,"/api/v1/transport/status","GET",null,Map.of()).statusCode()==200)return;}catch(java.io.IOException unavailable){}Thread.sleep(100);}throw new IllegalStateException("Delivery startup timeout");
  }
  static void stop()throws Exception{if(process!=null){process.destroy();if(!process.waitFor(8,TimeUnit.SECONDS)){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}process=null;}}
  static void sanitize(ProcessBuilder b){b.environment().keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("SERVER_")||k.startsWith("MANAGEMENT_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("_JAVA_OPTIONS"));}
  static void closed(int port)throws Exception{long end=System.nanoTime()+Duration.ofSeconds(5).toNanos();int refused=0;while(System.nanoTime()<end){try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),150);refused=0;}catch(java.io.IOException e){if(++refused==2)return;}Thread.sleep(50);}throw new AssertionError("Owned listener reachable "+port);}
  static String cli(String service,String action,byte[] stdin,String...args)throws Exception{
    var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend."+service+".delivery.DeliveryLabCli","-Dbackendlab.trust.config="+directory.resolve(service+".properties"),"-Dbackendlab.delivery.config="+directory.resolve(service+"-delivery.properties"),"-cp",jar(service).toString(),"org.springframework.boot.loader.launch.PropertiesLauncher",action));command.addAll(List.of(args));
    Path output=directory.resolve("delivery-cli-"+UUID.randomUUID()+".log");var b=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());sanitize(b);var child=b.start();
    try{try(var input=child.getOutputStream()){if(stdin!=null)input.write(stdin);}if(!child.waitFor(25,TimeUnit.SECONDS))throw new IllegalStateException("Owned CLI timeout");assertThat(child.exitValue()).as("Inspect retained bounded CLI diagnostic").isZero();String text=Files.readString(output);for(String line:text.lines().toList())if(Set.of("PENDING","DELIVERED","EMPTY","PAUSED","RETRY_SCHEDULED","STALE_LEASE").contains(line))return line;throw new IllegalStateException("CLI result missing");}
    finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  }
  static byte[] enqueue(UUID message,String stream)throws Exception{
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("messageId",message.toString());n.put("operationId",UUID.randomUUID().toString());n.put("correlationId",UUID.randomUUID().toString());n.put("producer",PRODUCER);n.put("consumer",SERVICE);n.put("environment","lab");n.put("authRealmId",realm.toString());n.put("producerGeneration",generations.get(PRODUCER).toString());n.put("participantGeneration",generations.get(SERVICE).toString());n.put("streamKey",stream);n.put("deliverySequence","1");n.put("type","LAB_APPLY");n.put("phase","APPLY");n.put("payloadBytes","eyJkZWx0YSI6MX0=");n.put("payloadDigest","2c5f28bf1e8d3e071e067f175a8b6f728081245b9b8313a9cce44a5aeff3f246");n.put("createdAt","2026-10-07T00:00:00.123Z");
    assertThat(cli(PRODUCER,"enqueue",WireCodec.JSON.writeValueAsBytes(n))).isEqualTo("PENDING");
    try(var c=source(PRODUCER,"app").getConnection();var s=c.prepareStatement("SELECT envelope_bytes FROM "+PRODUCER+".delivery_outbox WHERE message_id=?")){s.setObject(1,message);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getBytes(1);}}
  }
  static HttpResponse<byte[]> call(String caller,int port,String path,String method,byte[] body,Map<String,String> headers)throws Exception{
    try(var client=HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,directory.resolve(caller+".p12"),SERVICE)).connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build()){
      var q=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(8));if(body!=null&&!headers.containsKey("Content-Type"))q.header("Content-Type","application/json");headers.forEach(q::header);return client.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
  }
  static long count(String service,String table,String where)throws Exception{try(var c=source(service,"app").getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM "+service+"."+table+" WHERE "+where)){r.next();return r.getLong(1);}}
  @Test @Tag("DATA-15") void aeAuthLanePassesActualInt02WhileStrictPoisonRemains()throws Exception{
    // Independent of method order: a DATA15-only selector must create its own strict poison.
    UUID poisonMessage=UUID.randomUUID();byte[] poisonSource=enqueue(poisonMessage,"auth-independent-"+poisonMessage);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+poisonMessage,"PUT",changed(poisonSource,"payloadDigest","0".repeat(64)),Map.of()).statusCode()).isEqualTo(422);
    sql(PRODUCER,"UPDATE "+PRODUCER+".delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL WHERE message_id='"+poisonMessage+"'");
    assertThat(count(SERVICE,"delivery_quarantine","message_id='"+poisonMessage+"'")).isEqualTo(1);
    UUID op=UUID.randomUUID(),user=UUID.randomUUID();long floor=5;
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("operationId",op.toString());n.put("scope","ACCOUNT");n.put("action","RESET");n.put("audience",SERVICE);n.put("userId",user.toString());n.put("sessionRef",null);n.put("sessionContextId",null);n.put("epochFloor",Long.toString(floor));n.put("authRealmId",realm.toString());n.put("topologyVersion","r1-static-v1");n.put("participantGeneration",generations.get(SERVICE).toString());
    String intent="RESET\n"+user+"\nACCOUNT\n"+floor+"\n"+realm+"\nr1-static-v1\ncontent,learning";
    n.put("commandDigest",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(intent.getBytes(StandardCharsets.UTF_8))));
    long canonical=count("identity","auth_operation","true"),intents=count("identity","auth_fence_intent","true");
    assertThat(cli("identity","auth-enqueue",WireCodec.JSON.writeValueAsBytes(n))).isEqualTo("PENDING");
    assertThat(cli("identity","auth-dispatch",null,"8443")).isEqualTo("DELIVERED");
    assertThat(count("identity","auth_delivery_outbox","operation_id='"+op+"' AND consumer='"+SERVICE+"' AND delivery_status='DELIVERED' AND ack_bytes IS NOT NULL")).isEqualTo(1);
    assertThat(count(SERVICE,"auth_command_receipt","operation_id='"+op+"' AND applied_floor>=5")).isEqualTo(1);
    assertThat(count(SERVICE,"auth_account_gate","user_id='"+user+"' AND min_credential_epoch>=5")).isEqualTo(1);
    assertThat(count(SERVICE,"delivery_quarantine","message_id='"+poisonMessage+"'")).isEqualTo(1);
    assertThat(count("identity","auth_operation","true")).isEqualTo(canonical);assertThat(count("identity","auth_fence_intent","true")).isEqualTo(intents);
    // Actual existing INT02 + Identity auth-lane driver; no canonical completion/provider.
  }
  static void sql(String service,String query)throws Exception{try(var c=source(service,"migrator").getConnection();var s=c.createStatement()){s.execute(query);}}
  static byte[] changed(byte[] bytes,String field,Object value){var n=new LinkedHashMap<String,Object>();WireCodec.strict(bytes).properties().forEach(p->n.put(p.getKey(),p.getValue()));n.put(field,value);return WireCodec.JSON.writeValueAsBytes(n);}
  @Test @Tag("DATA-10") void aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects()throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"runtime-"+message);
    assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("DELIVERED");
    assertThat(count(PRODUCER,"transaction_probe","id='"+message+"'")).isEqualTo(1);assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isEqualTo(1);
    assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='DELIVERED' AND ack_bytes IS NOT NULL")).isEqualTo(1);
    assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"' AND disposition='APPLIED'")).isEqualTo(1);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of()).statusCode()).isEqualTo(200);
  }
  @Test @Tag("DATA-11") void abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect()throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"lost-ack-"+message);
    var first=call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of());assertThat(first.statusCode()).isEqualTo(200);
    // Intentionally discard response: producer outbox remains pending after actual receiver commit.
    assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='PENDING' AND ack_bytes IS NULL")).isEqualTo(1);
    stop();start();assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of()).body()).isEqualTo(first.body());assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("DELIVERED");
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",changed(exact,"operationId",UUID.randomUUID().toString()),Map.of()).statusCode()).isEqualTo(409);
    byte[] changedPayload="{\"delta\":2}".getBytes(StandardCharsets.UTF_8);
    byte[] conflicting=changed(changed(exact,"payloadBytes",Base64.getEncoder().encodeToString(changedPayload)),"payloadDigest",DeliveryWire.sha(changedPayload));
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",conflicting,Map.of()).statusCode()).isEqualTo(409);
    assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-13") void acPublicWrongCallerAndBrowserHeadersCannotDeliver()throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"boundary-"+message);
    assertThat(call("edge",8080,DeliveryWire.PATH+message,"PUT",exact,Map.of()).statusCode()).isEqualTo(404);
    for(String caller:List.of("operator",SERVICE,"edge"))assertThat(call(caller,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of()).statusCode()).isEqualTo(403);
    for(String header:List.of("Origin","Cookie","X-User-Id","X-Authz-Context","X-Forwarded-For"))assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of(header,"fixture-denied")).statusCode()).isEqualTo(403);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",new byte[DeliveryWire.MAX_ENVELOPE+1],Map.of()).statusCode()).isEqualTo(413);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT","{".getBytes(StandardCharsets.US_ASCII),Map.of()).statusCode()).isEqualTo(400);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of("Content-Type","text/plain")).statusCode()).isEqualTo(400);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"GET",exact,Map.of()).statusCode()).isIn(403,404,405);
    UUID schemaMessage=UUID.randomUUID();byte[] schema=enqueue(schemaMessage,"future-schema-"+schemaMessage);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+schemaMessage,"PUT",changed(schema,"schemaVersion",2),Map.of()).statusCode()).isEqualTo(422);
    assertThat(count(SERVICE,"delivery_quarantine","message_id='"+schemaMessage+"'")).isEqualTo(1);assertThat(count(SERVICE,"delivery_inbox","message_id='"+schemaMessage+"'")).isZero();
    pauseFaultFixture(schemaMessage); // No arbitrary repair of incompatible immutable bytes.
    assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"'")).isZero();assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("DELIVERED");
  }
  @Test @Tag("DATA-12") void adFutureGapAndBadDigestAreNoEffectDurablePoison()throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"poison-"+message);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",changed(exact,"deliverySequence","2"),Map.of()).statusCode()).isEqualTo(409);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",changed(exact,"payloadDigest","0".repeat(64)),Map.of()).statusCode()).isEqualTo(422);
    assertThat(count(SERVICE,"delivery_quarantine","message_id='"+message+"'")).isEqualTo(1);assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"'")).isZero();assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isZero();
    // Permanent poison is retained, not silently reserialized/skipped.
    assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("PAUSED");
  }
  static byte[] recoveryRequest(byte[] envelope,DeliveryStore.Recovery recovery,DeliveryStore.AbortProof proof,byte[] ack){
    var n=new LinkedHashMap<String,Object>();n.put("envelope",Base64.getEncoder().encodeToString(envelope));n.put("recoveryId",recovery.id().toString());n.put("recoveryGeneration",recovery.generation().toString());n.put("coveredSequence",Long.toString(recovery.sequence()));n.put("coveredPrefixDigest",recovery.prefix());n.put("reason",recovery.reason());n.put("proof",proof==null?null:Base64.getEncoder().encodeToString(proof.bytes()));n.put("ack",ack==null?null:Base64.getEncoder().encodeToString(ack));return WireCodec.JSON.writeValueAsBytes(n);
  }
  static byte[] recoveryFixture(String service,String action,byte[] input)throws Exception{
    Path classes=root.resolve("backend/services/"+service+"-service/target/test-classes").toRealPath();
    assertThat(classes.resolve("lab/backend/"+service+"/delivery/DeliveryRecoveryFixtureMain.class")).isRegularFile();
    var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend."+service+".delivery.DeliveryRecoveryFixtureMain","-Dloader.path="+classes,"-Dlab.config="+System.getProperty("lab.config"),"-Dtrust.config="+directory.resolve(service+".properties"),"-cp",jar(service).toString(),"org.springframework.boot.loader.launch.PropertiesLauncher",action);
    Path output=directory.resolve("recovery-fixture-"+UUID.randomUUID()+".log");var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());sanitize(builder);var child=builder.start();
    try{try(var stdin=child.getOutputStream()){stdin.write(input);}if(!child.waitFor(25,TimeUnit.SECONDS))throw new IllegalStateException("Owned recovery fixture timeout");assertThat(child.exitValue()).as("Inspect bounded fixture log").isZero();
      for(String line:Files.readString(output).lines().toList()){if(action.equals("accept")&&line.equals("ACCEPTED"))return line.getBytes(StandardCharsets.US_ASCII);if(!action.equals("accept")&&line.matches("[A-Za-z0-9+/]+={0,2}"))return Base64.getDecoder().decode(line);}
      throw new IllegalStateException("Recovery fixture result missing");
    }finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
  }
  static byte[] poisonTemplate(UUID message,String stream){
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("messageId",message.toString());n.put("operationId",UUID.randomUUID().toString());n.put("correlationId",UUID.randomUUID().toString());n.put("producer",PRODUCER);n.put("consumer",SERVICE);n.put("environment","lab");n.put("authRealmId",realm.toString());n.put("producerGeneration",generations.get(PRODUCER).toString());n.put("participantGeneration",generations.get(SERVICE).toString());n.put("streamKey",stream);n.put("deliverySequence","1");n.put("type","UNKNOWN_TYPE");n.put("phase","APPLY");n.put("payloadBytes","eyJkZWx0YSI6MX0=");n.put("payloadDigest","2c5f28bf1e8d3e071e067f175a8b6f728081245b9b8313a9cce44a5aeff3f246");n.put("createdAt","2026-10-07T00:00:00.123Z");return WireCodec.JSON.writeValueAsBytes(n);
  }
  static void maintenance(String service,boolean enabled)throws Exception{try(var c=source(service,"migrator").getConnection();var s=c.createStatement()){s.execute("UPDATE "+service+".delivery_environment SET maintenance="+enabled);}}
  @Test @Tag("DATA-13") void afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect()throws Exception{
    UUID message=UUID.randomUUID();String stream="recover-"+message;
    var recovery=new DeliveryStore.Recovery(UUID.randomUUID(),UUID.randomUUID(),0,DeliveryStore.ZERO,"LAB_PRECOMMIT_ABORT");
    byte[] exact=recoveryFixture(PRODUCER,"enqueue-poison",recoveryRequest(poisonTemplate(message,stream),recovery,null,null));
    var e=DeliveryWire.envelope(exact);assertThat(e.payloadVerified()).isTrue();
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of()).statusCode()).isEqualTo(422);
    assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isZero();
    var operator=new DeliveryStore(source(SERVICE,"migrator"),realm,generations.get(SERVICE),generations);
    maintenance(PRODUCER,true);maintenance(SERVICE,true);
    try{
      operator.authorizeRecovery(exact,recovery);var actualProof=new java.util.concurrent.atomic.AtomicReference<DeliveryStore.AbortProof>();
      var neverApply=new DeliveryStore.Consumer(){public void guard(Connection c,DeliveryWire.Envelope x){}public String poison(DeliveryWire.Envelope x){throw new AssertionError();}public void apply(Connection c,DeliveryWire.Envelope x){throw new AssertionError("No effect during recovery");}};
      var ack=operator.recoverRejected(exact,recovery,(x,r)->{try{var proof=new DeliveryStore.AbortProof(recoveryFixture(PRODUCER,"abort",recoveryRequest(exact,r,null,null)));actualProof.set(proof);return proof;}catch(Exception failure){throw new AssertionError("Actual own producer proof required",failure);}},neverApply);
      assertThat(ack.disposition()).isEqualTo("REJECTED_NO_EFFECT");
      assertThat(recoveryFixture(PRODUCER,"accept",recoveryRequest(exact,recovery,actualProof.get(),ack.bytes()))).isEqualTo("ACCEPTED".getBytes(StandardCharsets.US_ASCII));
      assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='REJECTED_NO_EFFECT' AND ack_bytes IS NOT NULL")).isEqualTo(1);
      assertThat(count(PRODUCER,"delivery_producer_abort","message_id='"+message+"'")).isEqualTo(1);assertThat(count(SERVICE,"delivery_recovery_receipt","message_id='"+message+"'")).isEqualTo(1);
      assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isZero();assertThat(count(SERVICE,"delivery_quarantine","message_id='"+message+"'")).isEqualTo(1);
      assertThat(operator.recoverRejected(exact,recovery,(x,r)->actualProof.get(),neverApply).bytes()).isEqualTo(ack.bytes());
    }finally{maintenance(PRODUCER,false);maintenance(SERVICE,false);}
    // Same immutable poison now yields the exact recovered receipt, never applies its payload.
    var duplicate=call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of());assertThat(duplicate.statusCode()).isEqualTo(200);assertThat(DeliveryWire.receipt(duplicate.body(),e).disposition()).isEqualTo("REJECTED_NO_EFFECT");
    UUID next=UUID.randomUUID();byte[] nextBytes=enqueue(next,stream);assertThat(DeliveryWire.envelope(nextBytes).sequence()).isEqualTo(2);
    assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("DELIVERED");assertThat(count(SERVICE,"transaction_probe","id='"+next+"'")).isEqualTo(1);assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isZero();
  }

  static Process dispatchProcess(int port)throws Exception{
    var command=List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dloader.main=lab.backend."+PRODUCER+".delivery.DeliveryLabCli","-Dbackendlab.trust.config="+directory.resolve(PRODUCER+".properties"),"-Dbackendlab.delivery.config="+directory.resolve(PRODUCER+"-delivery.properties"),"-cp",jar(PRODUCER).toString(),"org.springframework.boot.loader.launch.PropertiesLauncher","dispatch",Integer.toString(port));
    Path output=directory.resolve("crash-dispatch-"+UUID.randomUUID()+".log");var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());sanitize(builder);var child=builder.start();child.getOutputStream().close();return child;
  }
  static void producerFixtureSql(String query)throws Exception{try(var c=source(PRODUCER,"migrator").getConnection();var s=c.createStatement()){s.execute("SET lock_timeout='100ms'");s.execute(query);}}
  static byte[] storedEnvelope(UUID message)throws Exception{try(var c=source(PRODUCER,"app").getConnection();var s=c.prepareStatement("SELECT envelope_bytes FROM "+PRODUCER+".delivery_outbox WHERE message_id=?")){s.setObject(1,message);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getBytes(1);}}}
  static void pauseFaultFixture(UUID message)throws Exception{producerFixtureSql("UPDATE "+PRODUCER+".delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL WHERE message_id='"+message+"' AND delivery_status='PENDING'");}
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true}) @Tag("DATA-10")
  void agDispatcherProcessCrashBeforeAndAfterReceiverCommit(boolean commitFirst)throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"crash-"+message);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    try(var peer=new DeliveryFaultPeer(SERVICE,(socket,e)->{
      if(commitFirst){var response=call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",e.bytes(),Map.of());assertThat(response.statusCode()).isEqualTo(200);}
      entered.countDown();release.await(20,TimeUnit.SECONDS);
    })){
      var child=dispatchProcess(peer.port());
      try{
        assertThat(entered.await(4,TimeUnit.SECONDS)).as("Crash boundary reached before sender timeout").isTrue();assertThat(child.isAlive()).isTrue();
        assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='PENDING' AND lease_token IS NOT NULL AND attempts=1 AND ack_bytes IS NULL")).isEqualTo(1);
        // Actual DB-observed update cannot wait on sender's claimed row during network.
        producerFixtureSql("UPDATE "+PRODUCER+".delivery_outbox SET last_error='NETWORK_OUTSIDE_TX' WHERE message_id='"+message+"'");
        child.destroyForcibly();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();
        assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"'")).isEqualTo(commitFirst?1:0);
        assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='PENDING' AND ack_bytes IS NULL")).isEqualTo(1);
      }finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();}release.countDown();}
      assertThat(peer.requests.get()).isEqualTo(1);
    }
    if(commitFirst){stop();start();}
    // Owned-lab time boundary fixture, not runtime lease reset/unpause.
    producerFixtureSql("UPDATE "+PRODUCER+".delivery_outbox SET lease_until=clock_timestamp()-interval '1 second',next_attempt_at=clock_timestamp()-interval '1 second' WHERE message_id='"+message+"'");
    assertThat(cli(PRODUCER,"dispatch",null,"8443")).isEqualTo("DELIVERED");assertThat(storedEnvelope(message)).isEqualTo(exact);
    assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND attempts=2 AND delivery_status='DELIVERED'")).isEqualTo(1);
    assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isEqualTo(1);
  }
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"redirect","unavailable","oversize","stall"}) @Tag("DATA-13")
  void ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage(String mode)throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"network-"+message);var release=new CountDownLatch(1);
    try(var peer=new DeliveryFaultPeer(SERVICE,(socket,e)->{
      if(mode.equals("stall")){var output=socket.getOutputStream();output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 10\r\nConnection: close\r\n\r\n{".getBytes(StandardCharsets.US_ASCII));output.flush();release.await(12,TimeUnit.SECONDS);}
      else if(mode.equals("oversize"))DeliveryFaultPeer.response(socket,"200 OK","",new byte[DeliveryWire.MAX_ENVELOPE+1]);
      else DeliveryFaultPeer.response(socket,mode.equals("redirect")?"302 Found":"503 Service Unavailable",mode.equals("redirect")?"Location: https://127.0.0.1:1/forbidden\r\n":"",new byte[0]);
    })){
      try{long start=System.nanoTime();String result=cli(PRODUCER,"dispatch",null,Integer.toString(peer.port()));
        assertThat(result).isEqualTo(mode.equals("oversize")?"PAUSED":"RETRY_SCHEDULED");
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(15));
        assertThat(peer.requests.get()).as("No dispatcher retry or redirect in a single iteration").isEqualTo(1);
      }finally{release.countDown();}
    }
    assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"'")).isZero();assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isZero();assertThat(storedEnvelope(message)).isEqualTo(exact);
    assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND ack_bytes IS NULL AND attempts=1")).isEqualTo(1);
    pauseFaultFixture(message); // Isolate synthetic provider fault; never automatic success/unpause.
  }
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"receiver","operationId","phase","envelopeDigest","participantGeneration","receiptHead","durable"}) @Tag("DATA-14")
  void aiWrongAckFromAuthenticatedPeerCannotMarkDelivered(String field)throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"wrong-ack-"+message);
    try(var peer=new DeliveryFaultPeer(SERVICE,(socket,e)->{
      var actual=call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",e.bytes(),Map.of());assertThat(actual.statusCode()).isEqualTo(200);
      Object wrong=switch(field){case "receiver"->PRODUCER;case "operationId","participantGeneration"->UUID.randomUUID().toString();case "phase"->"WRONG_PHASE";case "durable"->false;default->"0".repeat(64);};
      DeliveryFaultPeer.response(socket,"200 OK","",changed(actual.body(),field,wrong));
    })){assertThat(cli(PRODUCER,"dispatch",null,Integer.toString(peer.port()))).isEqualTo("PAUSED");assertThat(peer.requests.get()).isEqualTo(1);}
    assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"' AND disposition='APPLIED'")).isEqualTo(1);assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isEqualTo(1);
    assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND delivery_status='PAUSED' AND ack_bytes IS NULL")).isEqualTo(1);assertThat(storedEnvelope(message)).isEqualTo(exact);
    assertThat(call(PRODUCER,8443,DeliveryWire.PATH+message,"PUT",exact,Map.of()).statusCode()).isEqualTo(200);
    assertThat(count(SERVICE,"transaction_probe","id='"+message+"'")).isEqualTo(1);
  }
  @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"edge","wrong-ca"}) @Tag("DATA-13")
  void ajWrongReceiverCertificateCannotMarkDelivered(String certificate)throws Exception{
    UUID message=UUID.randomUUID();byte[] exact=enqueue(message,"certificate-"+message);
    try(var peer=new DeliveryFaultPeer(certificate,(socket,e)->{throw new AssertionError("TLS must reject before HTTP");})){
      assertThat(cli(PRODUCER,"dispatch",null,Integer.toString(peer.port()))).isEqualTo("RETRY_SCHEDULED");assertThat(peer.requests.get()).isZero();
    }
    assertThat(count(SERVICE,"delivery_inbox","message_id='"+message+"'")).isZero();assertThat(count(PRODUCER,"delivery_outbox","message_id='"+message+"' AND ack_bytes IS NULL AND attempts=1")).isEqualTo(1);assertThat(storedEnvelope(message)).isEqualTo(exact);pauseFaultFixture(message);
    // edge: valid own CA/server EKU/IP but wrong named receiver. wrong-ca: invalid chain/EKU fixture.
  }

}
