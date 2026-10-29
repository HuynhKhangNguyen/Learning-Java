package lab.backend.learning.delivery;

import static org.assertj.core.api.Assertions.*;
import static lab.backend.learning.delivery.DeliveryWire.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;

/** Actual disposable PostgreSQL; profile follows fresh isolation/migration verification. */
@Timeout(30)
public class DeliveryStoreIT {
  static final UUID REALM=UUID.randomUUID();
  static final Map<String,UUID> GENERATIONS=Map.of("identity",UUID.randomUUID(),"content",UUID.randomUUID(),"learning",UUID.randomUUID());
  static final String PEER="content",SCHEMA="learning.";
  static final Properties config=new Properties();
  static PGSimpleDataSource app,migrator;static DeliveryStore store;
  @BeforeAll static void setup()throws Exception{
    String file=System.getProperty("lab.config");if(file==null||file.startsWith("$"+"{"))throw new IllegalStateException("Actual owned PostgreSQL required");
    Path path=Path.of(file).toRealPath();assertThat(path.getParent().resolve("ownership.json")).isRegularFile();try(var reader=Files.newBufferedReader(path)){config.load(reader);}app=source("app");migrator=source("migrator");
    assertThat(text(app,"SELECT current_setting('backend_lab.run_id')")).isEqualTo(config.getProperty("lab.runId"));
    sql(migrator,"INSERT INTO "+SCHEMA+"delivery_environment(singleton,realm_id,db_generation,identity_generation,content_generation,learning_generation,ready) VALUES (true,'"+REALM+"','"+GENERATIONS.get(SERVICE)+"','"+GENERATIONS.get("identity")+"','"+GENERATIONS.get("content")+"','"+GENERATIONS.get("learning")+"',true)");
    store=instance();store.verifyRuntime();
  }
  static DeliveryStore instance(){return new DeliveryStore(app,REALM,GENERATIONS.get(SERVICE),GENERATIONS);}
  static PGSimpleDataSource source(String kind){var s=new PGSimpleDataSource();s.setServerNames(new String[]{"127.0.0.1"});s.setPortNumbers(new int[]{Integer.parseInt(config.getProperty("lab.port"))});s.setDatabaseName(SERVICE+"_db");s.setUser(config.getProperty(SERVICE+"."+kind+".user"));s.setPassword(config.getProperty(SERVICE+"."+kind+".password"));s.setConnectTimeout(1);s.setSocketTimeout(8);s.setSslMode("disable");return s;}
  @BeforeEach void isolateDispatchFixtures()throws Exception{
    // Owned-lab operator fixture, not a runtime retry/unpause/reset path.
    sql(migrator,"UPDATE "+SCHEMA+"delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL WHERE delivery_status='PENDING'");
  }
  static String stream(){return "fixture-"+UUID.randomUUID();}
  static Envelope send(UUID id,String key,DeliveryStore.Work effect){return store.enqueue(id,UUID.randomUUID(),UUID.randomUUID(),PEER,key,"LAB_APPLY","APPLY","{\"delta\":1}".getBytes(),effect);}
  static Envelope incoming(UUID id,String key,long seq){
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("messageId",id.toString());n.put("operationId",UUID.randomUUID().toString());n.put("correlationId",UUID.randomUUID().toString());n.put("producer",PEER);n.put("consumer",SERVICE);n.put("environment","lab");n.put("authRealmId",REALM.toString());n.put("producerGeneration",GENERATIONS.get(PEER).toString());n.put("participantGeneration",GENERATIONS.get(SERVICE).toString());n.put("streamKey",key);n.put("deliverySequence",Long.toString(seq));n.put("type","LAB_APPLY");n.put("phase","APPLY");n.put("payloadBytes","eyJkZWx0YSI6MX0=");n.put("payloadDigest","2c5f28bf1e8d3e071e067f175a8b6f728081245b9b8313a9cce44a5aeff3f246");n.put("createdAt","2026-10-07T00:00:00.123Z");return envelope(lab.backend.learning.transport.WireCodec.JSON.writeValueAsBytes(n));
  }
  static byte[] changed(byte[] bytes,String key,Object value){var m=new LinkedHashMap<String,Object>();lab.backend.learning.transport.WireCodec.strict(bytes).properties().forEach(p->m.put(p.getKey(),p.getValue()));m.put(key,value);return lab.backend.learning.transport.WireCodec.JSON.writeValueAsBytes(m);}
  static void effect(Connection c,UUID id)throws SQLException{try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"transaction_probe VALUES (?,1,'effect')")){s.setString(1,id.toString());s.executeUpdate();}}
  static DeliveryStore.Consumer consumer(){return new DeliveryStore.Consumer(){public void guard(Connection c,Envelope e){}public String poison(Envelope e){return e.type().equals("LAB_APPLY")&&e.phase().equals("APPLY")?null:"UNSUPPORTED_DELIVERY_TYPE";}public void apply(Connection c,Envelope e)throws SQLException{effect(c,e.message());}};}
  @Test @Tag("DATA-10") void producerRollbackAndRetryKeepStateSequenceAndBytesAtomic()throws Exception{
    UUID id=UUID.randomUUID(),op=UUID.randomUUID(),cor=UUID.randomUUID();String key=stream();byte[] payload="{\"delta\":1}".getBytes();
    denied(()->store.enqueue(id,op,cor,PEER,key,"LAB_APPLY","APPLY",payload,c->{effect(c,id);throw new SQLException("after effect");}),"DELIVERY_UNAVAILABLE");
    assertThat(count("delivery_outbox","message_id='"+id+"'")).isZero();assertThat(count("transaction_probe","id='"+id+"'")).isZero();assertThat(count("delivery_producer_stream","stream_key='"+key+"'")).isZero();
    var first=store.enqueue(id,op,cor,PEER,key,"LAB_APPLY","APPLY",payload,c->effect(c,id));assertThat(first.sequence()).isEqualTo(1);
    var duplicate=instance().enqueue(id,op,cor,PEER,key,"LAB_APPLY","APPLY",payload,c->{throw new SQLException("must not repeat");});assertThat(duplicate.bytes()).isEqualTo(first.bytes());
    denied(()->store.enqueue(id,op,cor,PEER,key,"LAB_APPLY","APPLY","{}".getBytes(),c->{}),"MESSAGE_CONFLICT");
    assertThat(count("transaction_probe","id='"+id+"'")).isEqualTo(1);
  }
  @ParameterizedTest @ValueSource(strings={"commit","rollback","close","setAutoCommit","unwrap"}) @Tag("DATA-10")
  void callbackCannotTakeTransactionOwnership(String method)throws Exception{
    UUID id=UUID.randomUUID();denied(()->send(id,stream(),c->{effect(c,id);switch(method){case "commit"->c.commit();case "rollback"->c.rollback();case "close"->c.close();case "setAutoCommit"->c.setAutoCommit(true);case "unwrap"->c.unwrap(Connection.class);}}),"DELIVERY_UNAVAILABLE");
    assertThat(count("transaction_probe","id='"+id+"'")).isZero();assertThat(count("delivery_outbox","message_id='"+id+"'")).isZero();
  }
  @Test @Tag("DATA-10") void receiverRollbackLostAckAndRestartNeverRepeatEffect()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);var broken=new DeliveryStore.Consumer(){public void guard(Connection c,Envelope e){}public String poison(Envelope e){return null;}public void apply(Connection c,Envelope e)throws SQLException{effect(c,e.message());throw new SQLException("after effect before receipt");}};
    denied(()->store.receive(e.bytes(),PEER,broken),"DELIVERY_UNAVAILABLE");
    assertThat(count("transaction_probe","id='"+e.message()+"'")).isZero();assertThat(count("delivery_inbox","message_id='"+e.message()+"'")).isZero();assertThat(count("delivery_receiver_stream","stream_key='"+e.stream()+"'")).isZero();
    byte[] ack=store.receive(e.bytes(),PEER,consumer()).acknowledged();assertThat(instance().receive(e.bytes(),PEER,consumer()).acknowledged()).isEqualTo(ack);assertThat(receipt(ack,e).disposition()).isEqualTo("APPLIED");
    assertThat(count("transaction_probe","id='"+e.message()+"'")).isEqualTo(1);
    denied(()->store.receive(changed(e.bytes(),"operationId",UUID.randomUUID().toString()),PEER,consumer()),"MESSAGE_CONFLICT");
    denied(()->store.receive(changed(e.bytes(),"messageId",UUID.randomUUID().toString()),PEER,consumer()),"MESSAGE_CONFLICT");
  }
  @Test @Tag("DATA-11") void twoReplicasSerializeThroughCommitAndOneEffect()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pid=new AtomicInteger();var calls=new AtomicInteger();
    var blocking=new DeliveryStore.Consumer(){public void guard(Connection c,Envelope e){}public String poison(Envelope e){return null;}public void apply(Connection c,Envelope e)throws SQLException{calls.incrementAndGet();effect(c,e.message());try(var s=c.createStatement();var r=s.executeQuery("SELECT pg_backend_pid()")){r.next();pid.set(r.getInt(1));}held.countDown();try{if(!release.await(4,TimeUnit.SECONDS))throw new SQLException("barrier timeout");}catch(InterruptedException ex){throw new SQLException(ex);}}};
    try(var pool=Executors.newFixedThreadPool(2)){
      var first=pool.submit(()->store.receive(e.bytes(),PEER,blocking).acknowledged());assertThat(held.await(3,TimeUnit.SECONDS)).isTrue();var second=pool.submit(()->instance().receive(e.bytes(),PEER,blocking).acknowledged());
      try{long until=System.nanoTime()+Duration.ofSeconds(2).toNanos();boolean observed=false;while(System.nanoTime()<until){if(number(app,"SELECT count(*) FROM pg_stat_activity WHERE "+pid.get()+"=ANY(pg_blocking_pids(pid))")>0){observed=true;break;}Thread.sleep(10);}
        assertThat(observed).as("Actual server-observed blocking").isTrue();assertThat(second.isDone()).isFalse();assertThat(count("transaction_probe","id='"+e.message()+"'")).isZero();assertThat(count("delivery_inbox","message_id='"+e.message()+"'")).isZero();
      }finally{release.countDown();}
      assertThat(second.get(4,TimeUnit.SECONDS)).isEqualTo(first.get(4,TimeUnit.SECONDS));
    }assertThat(calls.get()).isEqualTo(1);
  }
  @Test @Tag("DATA-11") void currentGuardIsCheckedBeforeDuplicateReceipt()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);store.receive(e.bytes(),PEER,consumer()).acknowledged();
    var deniedGuard=new DeliveryStore.Consumer(){public void guard(Connection c,Envelope e)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SELECT 1")){r.next();throw deny(403,"CURRENT_SUPPRESSION");}}public String poison(Envelope e){throw new AssertionError();}public void apply(Connection c,Envelope e){throw new AssertionError();}};
    denied(()->instance().receive(e.bytes(),PEER,deniedGuard),"CURRENT_SUPPRESSION");assertThat(count("transaction_probe","id='"+e.message()+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-11") void newMessageIdCannotRepeatOneOperationPhaseEffect()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);store.receive(e.bytes(),PEER,consumer()).acknowledged();UUID different=UUID.randomUUID();
    byte[] replay=changed(changed(e.bytes(),"messageId",different.toString()),"deliverySequence","2");
    denied(()->store.receive(replay,PEER,consumer()),"MESSAGE_CONFLICT");
    assertThat(count("transaction_probe","id='"+different+"'")).isZero();assertThat(count("delivery_inbox","operation_id='"+e.operation()+"'")).isEqualTo(1);
    assertThat(number(app,"SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE stream_key='"+e.stream()+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-12") void gapHasNoEffectAndUnprovenOldPrefixCannotAck()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),2);assertThat(store.receive(e.bytes(),PEER,consumer()).error()).isEqualTo("STREAM_GAP");
    assertThat(number(app,"SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE stream_key='"+e.stream()+"'")).isZero();assertThat(count("transaction_probe","id='"+e.message()+"'")).isZero();
    // Missing-retention fixture only: no production compaction/reset operation.
    sql(migrator,"UPDATE "+SCHEMA+"delivery_receiver_stream SET last_sequence=1,receipt_head='"+ "a".repeat(64)+"' WHERE stream_key='"+e.stream()+"'");
    assertThat(store.receive(changed(e.bytes(),"deliverySequence","1"),PEER,consumer()).error()).isEqualTo("RESYNC_REQUIRED");assertThat(count("delivery_inbox","stream_key='"+e.stream()+"'")).isZero();
  }
  @ParameterizedTest @ValueSource(strings={"schemaVersion","payloadDigest","type"}) @Tag("DATA-13")
  void poisonQuarantinesMetadataWithoutAdvanceOrPrivatePayload(String field)throws Exception{
    var original=incoming(UUID.randomUUID(),stream(),1);var e=envelope(changed(original.bytes(),field,field.equals("schemaVersion")?2:field.equals("payloadDigest")?"0".repeat(64):"UNKNOWN_TYPE"));
    assertThat(store.receive(e.bytes(),PEER,consumer()).error()).isEqualTo("POISON_QUARANTINED");assertThat(instance().receive(e.bytes(),PEER,consumer()).error()).isEqualTo("POISON_QUARANTINED");
    assertThat(count("delivery_quarantine","message_id='"+e.message()+"'")).isEqualTo(1);assertThat(count("delivery_inbox","message_id='"+e.message()+"'")).isZero();assertThat(count("transaction_probe","id='"+e.message()+"'")).isZero();assertThat(number(app,"SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE stream_key='"+e.stream()+"'")).isZero();
    denied(()->store.receive(changed(e.bytes(),"correlationId",UUID.randomUUID().toString()),PEER,consumer()),"MESSAGE_CONFLICT");
    assertThat(number(app,"SELECT count(*) FROM information_schema.columns WHERE table_schema='"+SERVICE+"' AND table_name='delivery_quarantine' AND column_name IN ('payload','payload_bytes','envelope_bytes')")).isZero();
  }
  @ParameterizedTest @ValueSource(strings={"caller","authRealmId","producerGeneration","participantGeneration","consumer"}) @Tag("DATA-14")
  void wrongPeerRealmAndGenerationCannotCreateReceipt(String field)throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);String caller=field.equals("caller")?SERVICE:PEER;byte[] bytes=field.equals("caller")?e.bytes():changed(e.bytes(),field,field.equals("consumer")?PEER:UUID.randomUUID().toString());
    assertThatThrownBy(()->store.receive(bytes,caller,consumer())).isInstanceOf(lab.backend.learning.transport.WireCodec.Denied.class);assertThat(count("delivery_inbox","message_id='"+e.message()+"'")).isZero();assertThat(count("delivery_quarantine","message_id='"+e.message()+"'")).isZero();
  }
  @Test @Tag("DATA-10") void twoClaimantsGetOneLeaseAndNoDbLockIsHeldForNetwork()throws Exception{
    var e=send(UUID.randomUUID(),stream(),c->{});var start=new CountDownLatch(1);
    try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return store.claim();});var b=pool.submit(()->{start.await();return instance().claim();});start.countDown();var first=a.get(3,TimeUnit.SECONDS);var second=b.get(3,TimeUnit.SECONDS);assertThat((first.isPresent()?1:0)+(second.isPresent()?1:0)).isEqualTo(1);var lease=first.orElseGet(second::orElseThrow);assertThat(lease.envelope().bytes()).isEqualTo(e.bytes());
      try(var c=app.getConnection();var s=c.createStatement()){s.execute("SET lock_timeout='100ms'");assertThat(s.executeUpdate("UPDATE "+SCHEMA+"delivery_outbox SET last_error='NETWORK_OUTSIDE_TX' WHERE message_id='"+e.message()+"'")).isEqualTo(1);}
    }
  }
  @Test @Tag("DATA-10") void staleLeaseCannotChangeNewClaimOrAcknowledge()throws Exception{
    var e=send(UUID.randomUUID(),stream(),c->{});var first=store.claim().orElseThrow();sql(migrator,"UPDATE "+SCHEMA+"delivery_outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE message_id='"+e.message()+"'");
    var next=instance().claim().orElseThrow();assertThat(next.token()).isNotEqualTo(first.token());assertThat(next.attempt()).isEqualTo(2);assertThat(next.envelope().bytes()).isEqualTo(first.envelope().bytes());byte[] ack=receipt(e,"APPLIED",head(DeliveryStore.ZERO,e,"APPLIED")).bytes();
    assertThat(store.delivered(first,ack)).isFalse();assertThat(store.failed(first,"TIMEOUT",false,0)).isFalse();assertThat(store.delivered(next,ack)).isTrue();assertThat(instance().claim()).isEmpty();
  }
  @ParameterizedTest @ValueSource(strings={"receiver","operationId","envelopeDigest","participantGeneration","disposition","receiptHead"}) @Tag("DATA-14")
  void wrongAckNeverMarksDelivered(String field)throws Exception{
    var e=send(UUID.randomUUID(),stream(),c->{});var lease=store.claim().orElseThrow();Object wrong=switch(field){case "receiver"->SERVICE;case "operationId","participantGeneration"->UUID.randomUUID().toString();case "disposition"->"REJECTED_NO_EFFECT";default->"0".repeat(64);};
    assertThatThrownBy(()->store.delivered(lease,changed(receipt(e,"APPLIED",head(DeliveryStore.ZERO,e,"APPLIED")).bytes(),field,wrong))).isInstanceOf(lab.backend.learning.transport.WireCodec.Denied.class);
    assertThat(count("delivery_outbox","message_id='"+e.message()+"' AND delivery_status='PENDING' AND ack_bytes IS NULL")).isEqualTo(1);
  }
  @Test @Tag("DATA-13") void retryExhaustionPreservesPausedHeadAndBlocksLaterStreamMessage()throws Exception{
    String key=stream();var first=send(UUID.randomUUID(),key,c->{});var later=send(UUID.randomUUID(),key,c->{});
    for(int attempt=1;attempt<=8;attempt++){var lease=instance().claim().orElseThrow();assertThat(lease.envelope().message()).isEqualTo(first.message());assertThat(lease.attempt()).isEqualTo(attempt);assertThat(store.failed(lease,"TIMEOUT",false,125)).isTrue();
      if(attempt<8){long millis=number(app,"SELECT round(extract(epoch FROM (next_attempt_at-clock_timestamp()))*1000)::bigint FROM "+SCHEMA+"delivery_outbox WHERE message_id='"+first.message()+"'");assertThat(millis).isBetween(DeliveryStore.retryDelayMillis(attempt,125)-1000,DeliveryStore.retryDelayMillis(attempt,125));assertThat(store.claim()).isEmpty();sql(migrator,"UPDATE "+SCHEMA+"delivery_outbox SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE message_id='"+first.message()+"'");}
    }assertThat(count("delivery_outbox","message_id='"+first.message()+"' AND attempts=8 AND delivery_status='PAUSED' AND ack_bytes IS NULL")).isEqualTo(1);assertThat(store.claim()).isEmpty();assertThat(count("delivery_outbox","message_id='"+later.message()+"' AND attempts=0 AND delivery_status='PENDING'")).isEqualTo(1);
  }
  @Test @Tag("DATA-14") void receiptHeadMustExtendExactDeliveredPrefix()throws Exception{
    String key=stream();var first=send(UUID.randomUUID(),key,c->{});var second=send(UUID.randomUUID(),key,c->{});
    var firstLease=store.claim().orElseThrow();String firstHead=head(DeliveryStore.ZERO,first,"APPLIED");
    assertThat(store.delivered(firstLease,receipt(first,"APPLIED",firstHead).bytes())).isTrue();
    var secondLease=store.claim().orElseThrow();assertThat(secondLease.envelope().message()).isEqualTo(second.message());
    denied(()->store.delivered(secondLease,receipt(second,"APPLIED",head(DeliveryStore.ZERO,second,"APPLIED")).bytes()),"ACK_PREFIX_DENIED");
    assertThat(count("delivery_outbox","message_id='"+second.message()+"' AND delivery_status='PENDING'")).isEqualTo(1);
    assertThat(store.delivered(secondLease,receipt(second,"APPLIED",head(firstHead,second,"APPLIED")).bytes())).isTrue();
  }
  @Test @Tag("DATA-13") void finalClaimCrashExpiresToPausedWithoutDroppingBytes()throws Exception{
    var e=send(UUID.randomUUID(),stream(),c->{});sql(migrator,"UPDATE "+SCHEMA+"delivery_outbox SET attempts=7 WHERE message_id='"+e.message()+"'");assertThat(store.claim().orElseThrow().attempt()).isEqualTo(8);assertThat(store.claim()).isEmpty();sql(migrator,"UPDATE "+SCHEMA+"delivery_outbox SET lease_until=clock_timestamp()-interval '1 second' WHERE message_id='"+e.message()+"'");assertThat(instance().claim()).isEmpty();assertThat(count("delivery_outbox","message_id='"+e.message()+"' AND delivery_status='PAUSED' AND last_error='RETRY_EXHAUSTED'")).isEqualTo(1);
  }
  @Test @Tag("DATA-13") void runtimeCannotRewriteBytesReceiptsEnvironmentOrSkipSequences()throws Exception{
    var e=send(UUID.randomUUID(),stream(),c->{});var r=incoming(UUID.randomUUID(),stream(),1);store.receive(r.bytes(),PEER,consumer()).acknowledged();
    for(String query:List.of("UPDATE "+SCHEMA+"delivery_outbox SET envelope_bytes='x'::bytea WHERE message_id='"+e.message()+"'","UPDATE "+SCHEMA+"delivery_producer_stream SET next_sequence=next_sequence+2 WHERE stream_key='"+e.stream()+"'","INSERT INTO "+SCHEMA+"delivery_producer_stream(consumer,stream_key,realm_id,producer_generation,next_sequence) VALUES ('"+PEER+"','"+stream()+"','"+REALM+"','"+GENERATIONS.get(SERVICE)+"',2)","UPDATE "+SCHEMA+"delivery_receiver_stream SET last_sequence=last_sequence+2 WHERE stream_key='"+r.stream()+"'","UPDATE "+SCHEMA+"delivery_environment SET ready=false","DELETE FROM "+SCHEMA+"delivery_inbox WHERE message_id='"+r.message()+"'","UPDATE "+SCHEMA+"delivery_inbox SET receipt_head='"+ "f".repeat(64)+"' WHERE message_id='"+r.message()+"'"))assertThatThrownBy(()->sql(app,query)).isInstanceOf(SQLException.class);
    assertThat(count("delivery_inbox","message_id='"+r.message()+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-13") void disabledRequiredTriggerCannotPassRuntimeReadiness()throws Exception{
    sql(migrator,"ALTER TABLE "+SCHEMA+"delivery_outbox DISABLE TRIGGER delivery_outbox_immutable");
    try{denied(()->instance().verifyRuntime(),"DELIVERY_NOT_READY");}finally{sql(migrator,"ALTER TABLE "+SCHEMA+"delivery_outbox ENABLE TRIGGER delivery_outbox_immutable");}
    instance().verifyRuntime();
  }
  @Test @Tag("DATA-12") void coveredPrefixRequiresEveryRetainedBoundReceiptAndExactCursor()throws Exception{
    String key=stream();var first=incoming(UUID.randomUUID(),key,1);var second=incoming(UUID.randomUUID(),key,2);var next=incoming(UUID.randomUUID(),key,3);
    store.receive(first.bytes(),PEER,consumer()).acknowledged();byte[] ack=store.receive(second.bytes(),PEER,consumer()).acknowledged();String prefix=receipt(ack,second).head();
    var proof=instance().verifyCoveredPrefix(next.bytes(),PEER,2,prefix);
    assertThat(proof.sequence()).isEqualTo(2);assertThat(proof.head()).isEqualTo(prefix);assertThat(proof.realm()).isEqualTo(REALM);
    denied(()->store.verifyCoveredPrefix(next.bytes(),PEER,1,head(DeliveryStore.ZERO,first,"APPLIED")),"PREFIX_PROOF_DENIED");
    denied(()->store.verifyCoveredPrefix(next.bytes(),PEER,2,"f".repeat(64)),"PREFIX_PROOF_DENIED");
    sql(migrator,"DELETE FROM "+SCHEMA+"delivery_inbox WHERE message_id='"+first.message()+"'");
    denied(()->store.verifyCoveredPrefix(next.bytes(),PEER,2,prefix),"PREFIX_PROOF_DENIED");
    assertThat(count("transaction_probe","id='"+second.message()+"'")).isEqualTo(1);assertThat(count("delivery_inbox","message_id='"+second.message()+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-12") void corruptRetainedReceiptCannotBecomeCoveredProof()throws Exception{
    String key=stream();var first=incoming(UUID.randomUUID(),key,1);var next=incoming(UUID.randomUUID(),key,2);String prefix=receipt(store.receive(first.bytes(),PEER,consumer()).acknowledged(),first).head();
    sql(migrator,"UPDATE "+SCHEMA+"delivery_inbox SET receipt_head='"+"f".repeat(64)+"' WHERE message_id='"+first.message()+"'");
    denied(()->store.verifyCoveredPrefix(next.bytes(),PEER,1,prefix),"PREFIX_PROOF_DENIED");
    assertThat(number(app,"SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE stream_key='"+key+"'")).isEqualTo(1);
  }
  @Test @Tag("DATA-13") void maintenanceAndWrongRuntimeGenerationFailClosed()throws Exception{
    var e=incoming(UUID.randomUUID(),stream(),1);sql(migrator,"UPDATE "+SCHEMA+"delivery_environment SET maintenance=true");try{denied(()->store.receive(e.bytes(),PEER,consumer()),"DELIVERY_NOT_READY");denied(()->store.claim(),"DELIVERY_NOT_READY");}finally{sql(migrator,"UPDATE "+SCHEMA+"delivery_environment SET maintenance=false");}
    var wrong=new HashMap<>(GENERATIONS);wrong.put(SERVICE,UUID.randomUUID());denied(()->new DeliveryStore(app,REALM,wrong.get(SERVICE),wrong).verifyRuntime(),"DELIVERY_NOT_READY");assertThat(count("delivery_inbox","message_id='"+e.message()+"'")).isZero();
  }
  static DeliveryStore operator(){return new DeliveryStore(migrator,REALM,GENERATIONS.get(SERVICE),GENERATIONS);}
  static DeliveryStore.Recovery recovery(){return new DeliveryStore.Recovery(UUID.randomUUID(),UUID.randomUUID(),0,DeliveryStore.ZERO,"LAB_PRECOMMIT_ABORT");}
  static void maintenance(boolean enabled)throws SQLException{sql(migrator,"UPDATE "+SCHEMA+"delivery_environment SET maintenance="+enabled);}
  // Controlled trusted status-port fixture for receiver-only cases, NOT evidence of a real producer.
  static DeliveryStore.AbortProof controlledAbort(Envelope e,DeliveryStore.Recovery r){
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("producer",e.producer());n.put("consumer",e.consumer());n.put("messageId",e.message().toString());n.put("operationId",e.operation().toString());n.put("authRealmId",e.realm().toString());n.put("producerGeneration",e.producerGeneration().toString());n.put("participantGeneration",e.participantGeneration().toString());n.put("envelopeDigest",e.digest());n.put("recoveryId",r.id().toString());n.put("recoveryGeneration",r.generation().toString());n.put("coveredSequence",Long.toString(r.sequence()));n.put("coveredPrefixDigest",r.prefix());n.put("state","TERMINAL_ABORT_PRECOMMIT");n.put("canonicalCommitted",false);return new DeliveryStore.AbortProof(lab.backend.learning.transport.WireCodec.JSON.writeValueAsBytes(n));
  }
  static DeliveryStore.AbortGuard labAbortGuard(){return (c,e)->{
    try(var s=c.prepareStatement("SELECT revision,payload FROM "+SCHEMA+"transaction_probe WHERE id=? FOR UPDATE")){s.setString(1,e.operation().toString());try(var r=s.executeQuery()){if(!r.next()||r.getLong(1)!=0||!Set.of("OPEN","ABORTED").contains(r.getString(2)))throw deny(409,"RECOVERY_COMMITTED_DENIED");}}
    try(var s=c.prepareStatement("UPDATE "+SCHEMA+"transaction_probe SET payload='ABORTED' WHERE id=? AND revision=0")){s.setString(1,e.operation().toString());if(s.executeUpdate()!=1)throw deny(409,"RECOVERY_COMMITTED_DENIED");}
  };}
  @Test @Tag("DATA-13") void ownProducerAbortRequiresOperatorMaintenanceAndAtomicTerminalGuard()throws Exception{
    UUID op=UUID.randomUUID();var e=store.enqueue(UUID.randomUUID(),op,UUID.randomUUID(),PEER,stream(),"UNKNOWN_TYPE","APPLY","{}".getBytes(),c->{try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"transaction_probe VALUES (?,0,'OPEN')")){s.setString(1,op.toString());s.executeUpdate();}});var r=recovery();
    denied(()->store.authorizeRecovery(e.bytes(),r),"RECOVERY_OPERATOR_DENIED");denied(()->operator().authorizeRecovery(e.bytes(),r),"DELIVERY_NOT_READY");
    maintenance(true);try{
      operator().authorizeRecovery(e.bytes(),r);
      denied(()->operator().abortBeforeCommit(e.bytes(),r,(c,x)->{labAbortGuard().terminalAbort(c,x);throw new SQLException("after terminal effect");}),"DELIVERY_UNAVAILABLE");
      assertThat(text(app,"SELECT payload FROM "+SCHEMA+"transaction_probe WHERE id='"+op+"'")).isEqualTo("OPEN");assertThat(count("delivery_producer_abort","message_id='"+e.message()+"'")).isZero();
      var proof=operator().abortBeforeCommit(e.bytes(),r,labAbortGuard());
      assertThat(operator().abortBeforeCommit(e.bytes(),r,(c,x)->{throw new AssertionError("Never repeat terminal effect");}).bytes()).isEqualTo(proof.bytes());
      assertThat(text(app,"SELECT payload FROM "+SCHEMA+"transaction_probe WHERE id='"+op+"'")).isEqualTo("ABORTED");
      assertThat(count("delivery_outbox","message_id='"+e.message()+"' AND delivery_status='ABORT_PENDING' AND ack_bytes IS NULL")).isEqualTo(1);
      var ack=receipt(e,"REJECTED_NO_EFFECT",head(DeliveryStore.ZERO,e,"REJECTED_NO_EFFECT"));operator().acceptRecovered(e.bytes(),r,proof,ack.bytes());operator().acceptRecovered(e.bytes(),r,proof,ack.bytes());
      assertThat(count("delivery_outbox","message_id='"+e.message()+"' AND delivery_status='REJECTED_NO_EFFECT'")).isEqualTo(1);
      for(String query:List.of("UPDATE "+SCHEMA+"delivery_producer_abort SET proof_digest='"+"f".repeat(64)+"' WHERE recovery_id='"+r.id()+"'","DELETE FROM "+SCHEMA+"delivery_producer_abort WHERE recovery_id='"+r.id()+"'","DELETE FROM "+SCHEMA+"delivery_recovery_authorization WHERE recovery_id='"+r.id()+"'"))assertThatThrownBy(()->sql(migrator,query)).isInstanceOf(SQLException.class);
    }finally{maintenance(false);}
  }
  @Test @Tag("DATA-13") void canonicalCommittedProducerCannotBeAbortedOrSkipped()throws Exception{
    UUID op=UUID.randomUUID();var e=store.enqueue(UUID.randomUUID(),op,UUID.randomUUID(),PEER,stream(),"UNKNOWN_TYPE","APPLY","{}".getBytes(),c->{try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"transaction_probe VALUES (?,1,'COMMITTED')")){s.setString(1,op.toString());s.executeUpdate();}});var r=recovery();
    maintenance(true);try{operator().authorizeRecovery(e.bytes(),r);denied(()->operator().abortBeforeCommit(e.bytes(),r,labAbortGuard()),"RECOVERY_COMMITTED_DENIED");assertThat(count("delivery_producer_abort","message_id='"+e.message()+"'")).isZero();assertThat(count("delivery_outbox","message_id='"+e.message()+"' AND delivery_status='PENDING'")).isEqualTo(1);}finally{maintenance(false);}
  }
  @Test @Tag("DATA-13") void receiverRecoveryIsAuditedNoEffectAndStatusLookupHoldsNoDbLocks()throws Exception{
    var e=envelope(changed(incoming(UUID.randomUUID(),stream(),1).bytes(),"type","UNKNOWN_TYPE"));assertThat(store.receive(e.bytes(),PEER,consumer()).error()).isEqualTo("POISON_QUARANTINED");var r=recovery();
    maintenance(true);try{
      operator().authorizeRecovery(e.bytes(),r);var proof=controlledAbort(e,r);
      var neverApply=new DeliveryStore.Consumer(){public void guard(Connection c,Envelope x){}public String poison(Envelope x){throw new AssertionError();}public void apply(Connection c,Envelope x){throw new AssertionError("Recovery never applies payload");}};
      var ack=operator().recoverRejected(e.bytes(),r,(x,y)->{try(var c=migrator.getConnection();var s=c.createStatement()){s.execute("SET lock_timeout='100ms'");s.execute("UPDATE "+SCHEMA+"delivery_environment SET lock_token=lock_token");}catch(SQLException failure){throw new AssertionError("Status lookup held DB lock",failure);}return proof;},neverApply);
      assertThat(ack.disposition()).isEqualTo("REJECTED_NO_EFFECT");assertThat(operator().recoverRejected(e.bytes(),r,(x,y)->proof,neverApply).bytes()).isEqualTo(ack.bytes());
      assertThat(count("transaction_probe","id='"+e.message()+"'")).isZero();assertThat(count("delivery_recovery_receipt","message_id='"+e.message()+"' AND covered_sequence=0")).isEqualTo(1);
      assertThat(count("delivery_quarantine","message_id='"+e.message()+"'")).isEqualTo(1);
      assertThat(number(app,"SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE stream_key='"+e.stream()+"'")).isEqualTo(1);
      assertThatThrownBy(()->sql(migrator,"DELETE FROM "+SCHEMA+"delivery_recovery_receipt WHERE recovery_id='"+r.id()+"'")).isInstanceOf(SQLException.class);
    }finally{maintenance(false);}
    assertThat(store.receive(e.bytes(),PEER,consumer()).acknowledged()).isNotEmpty();
  }
  @Test @Tag("DATA-13") void missingAuthorizationCommittedProofAndWrongPrefixKeepPoisonUnchanged()throws Exception{
    var e=envelope(changed(incoming(UUID.randomUUID(),stream(),1).bytes(),"type","UNKNOWN_TYPE"));store.receive(e.bytes(),PEER,consumer());var r=recovery();
    maintenance(true);try{
      denied(()->operator().recoverRejected(e.bytes(),r,(x,y)->{throw new AssertionError("No authority lookup without authorization");},consumer()),"RECOVERY_PROOF_DENIED");
      operator().authorizeRecovery(e.bytes(),r);
      var committed=new DeliveryStore.AbortProof(changed(controlledAbort(e,r).bytes(),"canonicalCommitted",true));denied(()->operator().recoverRejected(e.bytes(),r,(x,y)->committed,consumer()),"RECOVERY_PROOF_DENIED");
      var newId=new DeliveryStore.Recovery(UUID.randomUUID(),UUID.randomUUID(),0,DeliveryStore.ZERO,"LAB_PRECOMMIT_ABORT");denied(()->operator().authorizeRecovery(e.bytes(),newId),"RECOVERY_PROOF_DENIED");
      var oldGeneration=new DeliveryStore.Recovery(UUID.randomUUID(),GENERATIONS.get(SERVICE),0,DeliveryStore.ZERO,"LAB_PRECOMMIT_ABORT");denied(()->operator().authorizeRecovery(e.bytes(),oldGeneration),"RECOVERY_PROOF_DENIED");
      var wrong=envelope(changed(incoming(UUID.randomUUID(),stream(),1).bytes(),"type","UNKNOWN_TYPE"));maintenance(false);store.receive(wrong.bytes(),PEER,consumer());maintenance(true);
      var badPrefix=new DeliveryStore.Recovery(UUID.randomUUID(),UUID.randomUUID(),0,"f".repeat(64),"LAB_PRECOMMIT_ABORT");operator().authorizeRecovery(wrong.bytes(),badPrefix);denied(()->operator().recoverRejected(wrong.bytes(),badPrefix,(x,y)->controlledAbort(x,y),consumer()),"PREFIX_PROOF_DENIED");
      assertThat(count("delivery_recovery_receipt","message_id='"+e.message()+"' OR message_id='"+wrong.message()+"'")).isZero();assertThat(count("delivery_inbox","message_id='"+e.message()+"' OR message_id='"+wrong.message()+"'")).isZero();
    }finally{maintenance(false);}
  }
  static void denied(Runnable run,String code){assertThatThrownBy(run::run).isInstanceOf(lab.backend.learning.transport.WireCodec.Denied.class).extracting("code").isEqualTo(code);}
  static void sql(PGSimpleDataSource source,String sql)throws SQLException{try(var c=source.getConnection();var s=c.createStatement()){s.execute(sql);}}
  static long count(String table,String predicate)throws SQLException{return number(app,"SELECT count(*) FROM "+SCHEMA+table+" WHERE "+predicate);}
  static long number(PGSimpleDataSource source,String sql)throws SQLException{try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
  static String text(PGSimpleDataSource source,String sql)throws SQLException{try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
}
