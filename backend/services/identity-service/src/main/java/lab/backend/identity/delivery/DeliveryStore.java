package lab.backend.identity.delivery;

import static lab.backend.identity.delivery.DeliveryWire.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import lab.backend.identity.transport.WireCodec;

/** Own-DB foundation. Callbacks are trusted local SQL ports, never network/domain authority. */
public final class DeliveryStore {
  public static final String ZERO="0".repeat(64);
  private final DataSource source;
  private final UUID realm,generation;
  private final Map<String,UUID> peers;
  private static final String SCHEMA="identity.";
  public DeliveryStore(DataSource source,UUID realm,UUID generation,Map<String,UUID> peers){
    this.source=Objects.requireNonNull(source);this.realm=Objects.requireNonNull(realm);this.generation=Objects.requireNonNull(generation);
    this.peers=Map.copyOf(peers);if(!this.peers.keySet().equals(SERVICES)||!generation.equals(this.peers.get(SERVICE)))throw new IllegalArgumentException("Three explicit own/peer generations required");
  }
  @FunctionalInterface public interface Work {void run(Connection connection)throws SQLException;}
  public interface Consumer {
    /** Locks/rereads current local suppression/phase guards, including on duplicate. */
    void guard(Connection connection,Envelope envelope)throws SQLException;
    /** Side-effect free typed schema/digest validation; null means supported. */
    String poison(Envelope envelope);
    /** Local SQL only on supplied connection. */
    void apply(Connection connection,Envelope envelope)throws SQLException;
  }
  public record Lease(Envelope envelope,UUID token,Instant until,int attempt){@Override public String toString(){return "DeliveryLease[message="+envelope.message()+"]";}}
  /** Retained, contiguous own-DB proof; never a guessed or compacted receipt. */
  public record Prefix(String producer,String receiver,String stream,UUID realm,UUID producerGeneration,
      UUID participantGeneration,long sequence,String head){}
  public record Recovery(UUID id,UUID generation,long sequence,String prefix,String reason){
    public Recovery{Objects.requireNonNull(id);Objects.requireNonNull(generation);if(sequence<0||prefix==null||!prefix.matches("[0-9a-f]{64}")||reason==null||!reason.matches("[A-Z][A-Z0-9_]{0,63}"))throw deny(409,"RECOVERY_PROOF_DENIED");}
  }
  public record AbortProof(byte[] bytes){
    public AbortProof{bytes=bytes.clone();if(bytes.length>MAX_ENVELOPE)throw deny(413,"ENVELOPE_TOO_LARGE");}
    @Override public byte[] bytes(){return bytes.clone();}
    @Override public String toString(){return "TerminalAbortProof[REDACTED]";}
  }
  /** Owning adapter must lock/reread canonical state and commit terminal pre-commit ABORT here.
   * It must never permit a subsequent canonical commit. No timeout/existence inference. */
  @FunctionalInterface public interface AbortGuard {void terminalAbort(Connection connection,Envelope envelope)throws SQLException;}
  /** Trusted authenticated named-producer status port. Lookup runs BEFORE receiver transaction.
   * R02c qualifies an own-DB fixture port, not a production publication status authority. */
  @FunctionalInterface public interface AbortAuthority {AbortProof lookup(Envelope envelope,Recovery recovery);}
  public void authorizeRecovery(byte[] bytes,Recovery recovery){
    Envelope e=envelope(bytes);recoveryBinding(e,recovery);
    transaction(c->{maintenanceOperator(c);environment(c,true);
      try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_recovery_authorization(recovery_id,recovery_generation,message_id,operation_id,producer,consumer,realm_id,producer_generation,participant_generation,offending_digest,covered_sequence,covered_prefix_digest,audit_reason) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")){
        authorizationKey(s,e,recovery);s.executeUpdate();
      }
      authorization(c,e,recovery);return null;
    });
  }
  public AbortProof abortBeforeCommit(byte[] bytes,Recovery recovery,AbortGuard guard){
    Envelope e=envelope(bytes);outgoing(e);recoveryBinding(e,recovery);
    return transaction(c->{maintenanceOperator(c);environment(c,true);authorization(c,e,recovery);
      Envelope stored=outbox(c,e.message());if(stored==null||!stored.digest().equals(e.digest()))throw deny(409,"RECOVERY_PROOF_DENIED");
      String status;
      try(var s=c.prepareStatement("SELECT delivery_status FROM "+SCHEMA+"delivery_outbox WHERE message_id=? FOR UPDATE")){s.setObject(1,e.message());try(var r=s.executeQuery()){r.next();status=r.getString(1);}}
      try(var s=c.prepareStatement("SELECT proof_bytes,proof_digest FROM "+SCHEMA+"delivery_producer_abort WHERE recovery_id=?")){s.setObject(1,recovery.id());try(var r=s.executeQuery()){if(r.next()){
        AbortProof proof=new AbortProof(r.getBytes(1));verifyAbort(proof,e,recovery);if(!sha(proof.bytes()).equals(r.getString(2)))throw deny(409,"RECOVERY_PROOF_DENIED");return proof;
      }}}
      if(Set.of("DELIVERED","REJECTED_NO_EFFECT").contains(status))throw deny(409,"RECOVERY_COMMITTED_DENIED");
      senderPrefix(c,e,recovery);guard.terminalAbort(local(c),e);
      AbortProof proof=createAbort(e,recovery);
      try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_producer_abort(recovery_id,message_id,proof_bytes,proof_digest) VALUES (?,?,?,?)")){
        s.setObject(1,recovery.id());s.setObject(2,e.message());s.setBytes(3,proof.bytes());s.setString(4,sha(proof.bytes()));s.executeUpdate();
      }
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET delivery_status='ABORT_PENDING',lease_token=NULL,lease_until=NULL,last_error='TERMINAL_ABORT_PRECOMMIT' WHERE message_id=?")){s.setObject(1,e.message());s.executeUpdate();}
      return proof;
    });
  }
  public Receipt recoverRejected(byte[] bytes,Recovery recovery,AbortAuthority authority,Consumer consumer){
    Envelope e=envelope(bytes);incoming(e,e.producer());recoveryBinding(e,recovery);
    transaction(c->{maintenanceOperator(c);environment(c,true);authorization(c,e,recovery);return null;});
    // Remote status retrieval must not hold environment/stream/domain locks.
    AbortProof proof=Objects.requireNonNull(authority.lookup(e,recovery));verifyAbort(proof,e,recovery);
    return transaction(c->{maintenanceOperator(c);environment(c,true);authorization(c,e,recovery);consumer.guard(local(c),e);
      try(var s=c.prepareStatement("SELECT i.ack_bytes,r.producer_abort_digest FROM "+SCHEMA+"delivery_recovery_receipt r JOIN "+SCHEMA+"delivery_inbox i USING(message_id) WHERE r.recovery_id=? AND r.message_id=?")){
        s.setObject(1,recovery.id());s.setObject(2,e.message());try(var r=s.executeQuery()){if(r.next()){
          if(!r.getString(2).equals(sha(proof.bytes())))throw deny(409,"RECOVERY_PROOF_DENIED");return receipt(r.getBytes(1),e);
        }}
      }
      try(var s=c.prepareStatement("SELECT last_sequence FROM "+SCHEMA+"delivery_receiver_stream WHERE producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? FOR UPDATE")){
        receiverKey(s,e);try(var r=s.executeQuery()){if(!r.next()||r.getLong(1)!=recovery.sequence())throw deny(409,"PREFIX_PROOF_DENIED");}
      }
      try(var s=c.prepareStatement("SELECT operation_id,envelope_digest,payload_digest,realm_id,producer_generation,participant_generation,delivery_sequence,producer,stream_key FROM "+SCHEMA+"delivery_quarantine WHERE message_id=?")){
        s.setObject(1,e.message());try(var r=s.executeQuery()){if(!r.next()||!e.operation().equals(r.getObject(1,UUID.class))||!e.digest().equals(r.getString(2))||!e.payloadDigest().equals(r.getString(3))||!e.realm().equals(r.getObject(4,UUID.class))||!e.producerGeneration().equals(r.getObject(5,UUID.class))||!generation.equals(r.getObject(6,UUID.class))||e.sequence()!=r.getLong(7)||!e.producer().equals(r.getString(8))||!e.stream().equals(r.getString(9)))throw deny(409,"RECOVERY_PROOF_DENIED");}
      }
      coveredPrefix(c,e,recovery.sequence(),recovery.prefix());
      Receipt ack=receipt(e,"REJECTED_NO_EFFECT",head(recovery.prefix(),e,"REJECTED_NO_EFFECT"));
      try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_recovery_receipt(recovery_id,message_id,realm_id,participant_generation,recovery_generation,covered_prefix_digest,offending_digest,producer_abort_digest,audit_reason,covered_sequence,producer_abort_bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){
        s.setObject(1,recovery.id());s.setObject(2,e.message());s.setObject(3,realm);s.setObject(4,generation);s.setObject(5,recovery.generation());s.setString(6,recovery.prefix());s.setString(7,e.digest());s.setString(8,sha(proof.bytes()));s.setString(9,recovery.reason());s.setLong(10,recovery.sequence());s.setBytes(11,proof.bytes());s.executeUpdate();
      }
      saveReceipt(c,e,ack);advance(c,e,ack.head());consumeAuthorization(c,recovery);return ack;
    });
  }
  public void acceptRecovered(byte[] bytes,Recovery recovery,AbortProof proof,byte[] ackBytes){
    Envelope e=envelope(bytes);outgoing(e);recoveryBinding(e,recovery);verifyAbort(proof,e,recovery);Receipt ack=receipt(ackBytes,e);
    if(!ack.disposition().equals("REJECTED_NO_EFFECT")||!ack.head().equals(head(recovery.prefix(),e,"REJECTED_NO_EFFECT")))throw deny(409,"RECOVERY_PROOF_DENIED");
    transaction(c->{maintenanceOperator(c);environment(c,true);authorization(c,e,recovery);
      try(var s=c.prepareStatement("SELECT p.proof_bytes,p.proof_digest,o.delivery_status,o.ack_bytes FROM "+SCHEMA+"delivery_producer_abort p JOIN "+SCHEMA+"delivery_outbox o USING(message_id) WHERE p.recovery_id=? AND p.message_id=? FOR UPDATE OF o")){
        s.setObject(1,recovery.id());s.setObject(2,e.message());try(var r=s.executeQuery()){
          if(!r.next()||!Arrays.equals(r.getBytes(1),proof.bytes())||!r.getString(2).equals(sha(proof.bytes())))throw deny(409,"RECOVERY_PROOF_DENIED");
          if(r.getString(3).equals("REJECTED_NO_EFFECT")){if(!Arrays.equals(r.getBytes(4),ack.bytes()))throw deny(409,"RECOVERY_PROOF_DENIED");return null;}
          if(!r.getString(3).equals("ABORT_PENDING"))throw deny(409,"RECOVERY_COMMITTED_DENIED");
        }
      }
      senderPrefix(c,e,recovery);
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET delivery_status='REJECTED_NO_EFFECT',ack_bytes=?,last_error=NULL WHERE message_id=? AND delivery_status='ABORT_PENDING'")){s.setBytes(1,ack.bytes());s.setObject(2,e.message());if(s.executeUpdate()!=1)throw deny(409,"RECOVERY_PROOF_DENIED");}
      consumeAuthorization(c,recovery);return null;
    });
  }
  private void maintenanceOperator(Connection c)throws SQLException{
    try(var s=c.createStatement();var r=s.executeQuery("SELECT current_database(),current_user")){r.next();if(!r.getString(1).equals(SERVICE+"_db")||!r.getString(2).equals(SERVICE+"_migrator"))throw deny(403,"RECOVERY_OPERATOR_DENIED");}
  }
  private void recoveryBinding(Envelope e,Recovery recovery){
    if(e.producer().equals(SERVICE))outgoing(e);else incoming(e,e.producer());
    if(recovery.generation().equals(realm)||peers.containsValue(recovery.generation())||recovery.sequence()!=e.sequence()-1)throw deny(409,"RECOVERY_PROOF_DENIED");
  }
  private void authorizationKey(PreparedStatement s,Envelope e,Recovery r)throws SQLException{
    s.setObject(1,r.id());s.setObject(2,r.generation());s.setObject(3,e.message());s.setObject(4,e.operation());s.setString(5,e.producer());s.setString(6,e.consumer());s.setObject(7,realm);s.setObject(8,e.producerGeneration());s.setObject(9,e.participantGeneration());s.setString(10,e.digest());s.setLong(11,r.sequence());s.setString(12,r.prefix());s.setString(13,r.reason());
  }
  private void authorization(Connection c,Envelope e,Recovery r)throws SQLException{
    try(var s=c.prepareStatement("SELECT recovery_id FROM "+SCHEMA+"delivery_recovery_authorization WHERE recovery_id=? AND recovery_generation=? AND message_id=? AND operation_id=? AND producer=? AND consumer=? AND realm_id=? AND producer_generation=? AND participant_generation=? AND offending_digest=? AND covered_sequence=? AND covered_prefix_digest=? AND audit_reason=? FOR UPDATE")){
      authorizationKey(s,e,r);try(var result=s.executeQuery()){if(!result.next())throw deny(409,"RECOVERY_PROOF_DENIED");}
    }
  }
  private void consumeAuthorization(Connection c,Recovery r)throws SQLException{try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_recovery_authorization SET state='CONSUMED' WHERE recovery_id=? AND state='AUTHORIZED'")){s.setObject(1,r.id());s.executeUpdate();}}
  private void senderPrefix(Connection c,Envelope e,Recovery recovery)throws SQLException{
    long sequence=0;String prefix=ZERO;
    try(var s=c.prepareStatement("SELECT envelope_bytes,ack_bytes,delivery_status FROM "+SCHEMA+"delivery_outbox WHERE consumer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND delivery_sequence<? ORDER BY delivery_sequence LIMIT 10001")){
      producerKey(s,e.consumer(),e.stream());s.setLong(5,e.sequence());try(var r=s.executeQuery()){while(r.next()){
        if(sequence==10_000||!Set.of("DELIVERED","REJECTED_NO_EFFECT").contains(r.getString(3))||r.getBytes(2)==null)throw deny(409,"PREFIX_PROOF_DENIED");
        Envelope previous=envelope(r.getBytes(1));outgoing(previous);Receipt ack=receipt(r.getBytes(2),previous);String disposition=r.getString(3).equals("DELIVERED")?"APPLIED":"REJECTED_NO_EFFECT";
        if(previous.sequence()!=++sequence||!ack.disposition().equals(disposition))throw deny(409,"PREFIX_PROOF_DENIED");
        if(disposition.equals("REJECTED_NO_EFFECT"))rejectedProvenance(c,previous,prefix);
        prefix=head(prefix,previous,disposition);if(!prefix.equals(ack.head()))throw deny(409,"PREFIX_PROOF_DENIED");
      }}
    }
    if(sequence!=recovery.sequence()||!prefix.equals(recovery.prefix()))throw deny(409,"PREFIX_PROOF_DENIED");
  }
  private static AbortProof createAbort(Envelope e,Recovery r){
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("producer",e.producer());n.put("consumer",e.consumer());n.put("messageId",e.message().toString());n.put("operationId",e.operation().toString());n.put("authRealmId",e.realm().toString());n.put("producerGeneration",e.producerGeneration().toString());n.put("participantGeneration",e.participantGeneration().toString());n.put("envelopeDigest",e.digest());n.put("recoveryId",r.id().toString());n.put("recoveryGeneration",r.generation().toString());n.put("coveredSequence",Long.toString(r.sequence()));n.put("coveredPrefixDigest",r.prefix());n.put("state","TERMINAL_ABORT_PRECOMMIT");n.put("canonicalCommitted",false);return new AbortProof(WireCodec.JSON.writeValueAsBytes(n));
  }
  private static void verifyAbort(AbortProof proof,Envelope e,Recovery r){
    byte[] expected=createAbort(e,r).bytes();var expectedNode=WireCodec.strict(expected);var actual=object(proof.bytes(),Set.of("schemaVersion","producer","consumer","messageId","operationId","authRealmId","producerGeneration","participantGeneration","envelopeDigest","recoveryId","recoveryGeneration","coveredSequence","coveredPrefixDigest","state","canonicalCommitted"));
    if(!actual.equals(expectedNode)||!Arrays.equals(proof.bytes(),expected))throw deny(409,"RECOVERY_PROOF_DENIED");
  }
  private void rejectedProvenance(Connection c,Envelope e,String previous)throws SQLException{
    boolean producer=e.producer().equals(SERVICE);
    String proof=producer?"p.proof_bytes":"p.producer_abort_bytes",digest=producer?"p.proof_digest":"p.producer_abort_digest";
    String table=producer?"delivery_producer_abort":"delivery_recovery_receipt";
    try(var s=c.prepareStatement("SELECT a.recovery_id,a.recovery_generation,a.covered_sequence,a.covered_prefix_digest,a.audit_reason,"+proof+","+digest+" FROM "+SCHEMA+table+" p JOIN "+SCHEMA+"delivery_recovery_authorization a USING(recovery_id) WHERE p.message_id=? AND a.message_id=? AND a.operation_id=? AND a.producer=? AND a.consumer=? AND a.realm_id=? AND a.producer_generation=? AND a.participant_generation=? AND a.offending_digest=? AND a.state='CONSUMED'")){
      s.setObject(1,e.message());s.setObject(2,e.message());s.setObject(3,e.operation());s.setString(4,e.producer());s.setString(5,e.consumer());s.setObject(6,e.realm());s.setObject(7,e.producerGeneration());s.setObject(8,e.participantGeneration());s.setString(9,e.digest());
      try(var r=s.executeQuery()){
        if(!r.next())throw deny(409,"PREFIX_PROOF_DENIED");Recovery recovery=new Recovery(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3),r.getString(4),r.getString(5));AbortProof abort=new AbortProof(r.getBytes(6));
        if(recovery.sequence()!=e.sequence()-1||!previous.equals(recovery.prefix())||!sha(abort.bytes()).equals(r.getString(7)))throw deny(409,"PREFIX_PROOF_DENIED");verifyAbort(abort,e,recovery);
      }
    }
  }
  public Prefix verifyCoveredPrefix(byte[] reference,String caller,long sequence,String expectedHead){
    Envelope e=envelope(reference);incoming(e,caller);
    if(sequence<0||sequence>=e.sequence()||expectedHead==null||!expectedHead.matches("[0-9a-f]{64}"))throw deny(409,"PREFIX_PROOF_DENIED");
    return transaction(c->{environment(c,false);return coveredPrefix(c,e,sequence,expectedHead);});
  }
  private Prefix coveredPrefix(Connection c,Envelope e,long sequence,String expectedHead)throws SQLException{
    long cursor;String cursorHead;
    try(var s=c.prepareStatement("SELECT last_sequence,receipt_head FROM "+SCHEMA+"delivery_receiver_stream WHERE producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? FOR SHARE")){
      receiverKey(s,e);try(var r=s.executeQuery()){if(!r.next())throw deny(409,"PREFIX_PROOF_DENIED");cursor=r.getLong(1);cursorHead=r.getString(2);}
    }
    if(cursor!=sequence)throw deny(409,"PREFIX_PROOF_DENIED");
    long verified=0;String verifiedHead=ZERO;
    try(var s=c.prepareStatement("SELECT message_id,operation_id,delivery_sequence,message_type,phase,envelope_digest,payload_digest,disposition,receipt_head,ack_bytes FROM "+SCHEMA+"delivery_inbox WHERE producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? ORDER BY delivery_sequence LIMIT 10001")){
      receiverKey(s,e);try(var r=s.executeQuery()){while(r.next()){
        // Foundation keeps receipts; no deletion/compaction or unbounded recovery scan here.
        if(verified==10_000||r.getLong(3)!=verified+1||r.getLong(3)>sequence)throw deny(409,"PREFIX_PROOF_DENIED");
        Envelope metadata=new Envelope(1,r.getObject(1,UUID.class),r.getObject(2,UUID.class),e.correlation(),e.producer(),SERVICE,e.realm(),e.producerGeneration(),generation,e.stream(),r.getLong(3),r.getString(4),r.getString(5),new byte[0],r.getString(7),Instant.EPOCH,new byte[0],r.getString(6));
        Receipt ack=receipt(r.getBytes(10),metadata);String disposition=r.getString(8);
        if(disposition.equals("REJECTED_NO_EFFECT"))rejectedProvenance(c,metadata,verifiedHead);
        verifiedHead=head(verifiedHead,metadata,disposition);
        if(!ack.disposition().equals(disposition)||!ack.head().equals(verifiedHead)||!r.getString(9).equals(verifiedHead))throw deny(409,"PREFIX_PROOF_DENIED");
        verified++;
      }}
    }
    if(verified!=sequence||!verifiedHead.equals(cursorHead)||!verifiedHead.equals(expectedHead))throw deny(409,"PREFIX_PROOF_DENIED");
    return new Prefix(e.producer(),SERVICE,e.stream(),realm,e.producerGeneration(),generation,sequence,verifiedHead);
  }
  public record Received(Receipt receipt,String error){
    public byte[] acknowledged(){if(error!=null)throw deny(error.equals("POISON_QUARANTINED")?422:409,error);return receipt.bytes();}
  }
  public void verifyRuntime(){
    transaction(c->{
      environment(c,false);
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE tgrelid IN ('"+SCHEMA+"delivery_recovery_authorization'::regclass,'"+SCHEMA+"delivery_producer_abort'::regclass,'"+SCHEMA+"delivery_recovery_receipt'::regclass) AND NOT tgisinternal AND tgenabled IN ('O','A') AND tgname IN ('delivery_recovery_authorization_immutable','delivery_producer_abort_immutable','delivery_recovery_receipt_immutable','delivery_recovery_authorization_retained')")){
        if(!r.next()||r.getInt(1)!=4)throw deny(503,"DELIVERY_NOT_READY");
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM pg_trigger WHERE tgrelid IN ('"+SCHEMA+"delivery_outbox'::regclass,'"+SCHEMA+"auth_delivery_outbox'::regclass,'"+SCHEMA+"delivery_producer_stream'::regclass,'"+SCHEMA+"delivery_receiver_stream'::regclass) AND NOT tgisinternal AND tgenabled IN ('O','A') AND tgname IN ('delivery_outbox_immutable','auth_delivery_immutable','delivery_producer_monotonic','delivery_receiver_monotonic','delivery_producer_initial','delivery_receiver_initial')")){
        if(!r.next()||r.getInt(1)!=6)throw deny(503,"DELIVERY_NOT_READY");
      }
      for(String[] constraint:List.of(new String[]{"delivery_outbox","UNIQUE (operation_id, consumer, message_type, phase)"},new String[]{"delivery_inbox","UNIQUE (operation_id, producer, message_type, phase, realm_id, producer_generation, participant_generation)"})){
        try(var s=c.prepareStatement("SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='u' AND convalidated AND pg_get_constraintdef(oid)=?")){
          s.setString(1,SCHEMA+constraint[0]);s.setString(2,constraint[1]);try(var r=s.executeQuery()){if(!r.next()||r.getInt(1)!=1)throw deny(503,"DELIVERY_NOT_READY");}
        }
      }
      try(var s=c.createStatement();var r=s.executeQuery("SELECT current_database(),current_user,has_table_privilege(current_user,'"+SCHEMA+"delivery_environment','UPDATE'),has_table_privilege(current_user,'"+SCHEMA+"delivery_inbox','UPDATE'),has_table_privilege(current_user,'"+SCHEMA+"delivery_recovery_receipt','INSERT')")){
        if(!r.next()||!r.getString(1).equals(SERVICE+"_db")||!r.getString(2).equals(SERVICE+"_app")||r.getBoolean(3)||r.getBoolean(4)||r.getBoolean(5))throw deny(503,"DELIVERY_NOT_READY");
      }
      return null;
    });
  }
  public Envelope enqueue(UUID message,UUID operation,UUID correlation,String consumer,String stream,String type,String phase,byte[] payload,Work work){
    // Parse all caller-controlled input before acquiring locks; timestamp/sequence below are DB-owned.
    create(message,operation,correlation,consumer,realm,generation,peer(consumer),stream,1,type,phase,payload,Instant.now());
    return transaction(c->{
      environment(c,false);lockProducer(c,consumer,stream);
      Envelope previous=outbox(c,message);
      if(previous!=null){
        if(!previous.operation().equals(operation)||!previous.correlation().equals(correlation)||!previous.consumer().equals(consumer)||!previous.realm().equals(realm)||!previous.producerGeneration().equals(generation)||!previous.participantGeneration().equals(peer(consumer))||!previous.stream().equals(stream)||!previous.type().equals(type)||!previous.phase().equals(phase)||!Arrays.equals(previous.payload(),payload))throw deny(409,"MESSAGE_CONFLICT");
        return previous;
      }
      long sequence;
      try(var s=c.prepareStatement("SELECT next_sequence FROM "+SCHEMA+"delivery_producer_stream WHERE consumer=? AND stream_key=? AND realm_id=? AND producer_generation=? FOR UPDATE")){
        producerKey(s,consumer,stream);try(var r=s.executeQuery()){r.next();sequence=r.getLong(1);}
      }
      if(sequence==Long.MAX_VALUE)throw deny(409,"SEQUENCE_EXHAUSTED");
      Envelope e=create(message,operation,correlation,consumer,realm,generation,peer(consumer),stream,sequence,type,phase,payload,time(c));
      work.run(local(c));
      try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_outbox(message_id,operation_id,correlation_id,consumer,stream_key,delivery_sequence,realm_id,producer_generation,participant_generation,message_type,phase,envelope_bytes,envelope_digest,payload_digest,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
        s.setObject(1,message);s.setObject(2,operation);s.setObject(3,correlation);s.setString(4,consumer);s.setString(5,stream);s.setLong(6,sequence);s.setObject(7,realm);s.setObject(8,generation);s.setObject(9,peer(consumer));s.setString(10,type);s.setString(11,phase);s.setBytes(12,e.bytes());s.setString(13,e.digest());s.setString(14,e.payloadDigest());s.setObject(15,e.createdAt().atOffset(ZoneOffset.UTC));s.executeUpdate();
      }
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_producer_stream SET next_sequence=next_sequence+1 WHERE consumer=? AND stream_key=? AND realm_id=? AND producer_generation=?")){producerKey(s,consumer,stream);s.executeUpdate();}
      environment(c,false);return e;
    });
  }
  public Received receive(byte[] bytes,String caller,Consumer consumer){
    Envelope e=envelope(bytes);incoming(e,caller);
    return transaction(c->{
      environment(c,false);consumer.guard(local(c),e);
      try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_receiver_stream(producer,stream_key,realm_id,producer_generation,participant_generation) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING")){receiverKey(s,e);s.executeUpdate();}
      long cursor;String head;
      try(var s=c.prepareStatement("SELECT last_sequence,receipt_head FROM "+SCHEMA+"delivery_receiver_stream WHERE producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? FOR UPDATE")){
        receiverKey(s,e);try(var r=s.executeQuery()){r.next();cursor=r.getLong(1);head=r.getString(2);}
      }
      try(var s=c.prepareStatement("SELECT message_id,envelope_digest,ack_bytes FROM "+SCHEMA+"delivery_inbox WHERE message_id=? OR (producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? AND delivery_sequence=?)")){
        s.setObject(1,e.message());receiverKey(s,e,2);s.setLong(7,e.sequence());try(var r=s.executeQuery()){if(r.next()){
          if(!e.message().equals(r.getObject(1,UUID.class))||!e.digest().equals(r.getString(2)))throw deny(409,"MESSAGE_CONFLICT");
          byte[] ack=r.getBytes(3);if(r.next())throw deny(409,"MESSAGE_CONFLICT");return new Received(receipt(ack,e),null);
        }}
      }
      if(cursor==Long.MAX_VALUE||e.sequence()<=cursor)return new Received(null,"RESYNC_REQUIRED");
      if(e.sequence()!=cursor+1)return new Received(null,"STREAM_GAP");
      String poison=e.version()!=1?"UNSUPPORTED_DELIVERY_SCHEMA":!e.payloadVerified()?"PAYLOAD_DIGEST_MISMATCH":consumer.poison(e);
      if(poison!=null){quarantine(c,e,poison);return new Received(null,"POISON_QUARANTINED");}
      // A quarantined message cannot be made valid by changing handler configuration.
      try(var s=c.prepareStatement("SELECT envelope_digest FROM "+SCHEMA+"delivery_quarantine WHERE message_id=?")){s.setObject(1,e.message());try(var r=s.executeQuery()){if(r.next()){if(!e.digest().equals(r.getString(1)))throw deny(409,"MESSAGE_CONFLICT");return new Received(null,"POISON_QUARANTINED");}}}
      consumer.apply(local(c),e);
      String next=head(head,e,"APPLIED");Receipt ack=receipt(e,"APPLIED",next);
      saveReceipt(c,e,ack);advance(c,e,next);environment(c,false);
      return new Received(ack,null);
    }); // No externally observable ACK before this transaction commits.
  }
  public Optional<Lease> claim(){
    return transaction(c->{
      environment(c,false);Instant now=time(c);
      // Expired final attempts remain durable, never dropped or auto-successful.
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET delivery_status='PAUSED',lease_token=NULL,lease_until=NULL,last_error='RETRY_EXHAUSTED' WHERE delivery_status='PENDING' AND attempts=8 AND (lease_until IS NULL OR lease_until<=?)")){s.setObject(1,offset(now));s.executeUpdate();}
      UUID message;
      String q="SELECT o.message_id FROM "+SCHEMA+"delivery_outbox o WHERE o.delivery_status='PENDING' AND o.attempts<8 AND o.next_attempt_at<=? AND (o.lease_until IS NULL OR o.lease_until<=?) AND o.realm_id=? AND o.producer_generation=? AND NOT EXISTS (SELECT 1 FROM "+SCHEMA+"delivery_outbox p WHERE p.consumer=o.consumer AND p.stream_key=o.stream_key AND p.realm_id=o.realm_id AND p.producer_generation=o.producer_generation AND p.delivery_sequence<o.delivery_sequence AND p.delivery_status NOT IN ('DELIVERED','REJECTED_NO_EFFECT')) ORDER BY o.next_attempt_at,o.created_at,o.message_id FOR UPDATE OF o SKIP LOCKED LIMIT 1";
      try(var s=c.prepareStatement(q)){s.setObject(1,offset(now));s.setObject(2,offset(now));s.setObject(3,realm);s.setObject(4,generation);try(var r=s.executeQuery()){if(!r.next())return Optional.empty();message=r.getObject(1,UUID.class);}}
      Envelope e=outbox(c,message);outgoing(e);
      UUID token=UUID.randomUUID();Instant until=now.plusSeconds(15);int attempt;
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET attempts=attempts+1,lease_token=?,lease_until=? WHERE message_id=? RETURNING attempts")){
        s.setObject(1,token);s.setObject(2,offset(until));s.setObject(3,message);try(var r=s.executeQuery()){r.next();attempt=r.getInt(1);}
      }
      return Optional.of(new Lease(e,token,until,attempt));
    });
  }
  public boolean delivered(Lease lease,byte[] ackBytes){
    Envelope candidate=envelope(lease.envelope().bytes());Receipt ack=receipt(ackBytes,candidate);
    if(!ack.disposition().equals("APPLIED"))throw deny(409,"ACK_DISPOSITION_DENIED");
    return transaction(c->{
      environment(c,false);Envelope stored=outbox(c,candidate.message());if(stored==null||!stored.digest().equals(candidate.digest()))throw deny(409,"MESSAGE_CONFLICT");outgoing(stored);
      String previous=ZERO;
      if(stored.sequence()>1){
        try(var s=c.prepareStatement("SELECT envelope_bytes,ack_bytes,delivery_status FROM "+SCHEMA+"delivery_outbox WHERE consumer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND delivery_sequence=?")){
          producerKey(s,stored.consumer(),stored.stream());s.setLong(5,stored.sequence()-1);
          try(var r=s.executeQuery()){
            if(!r.next()||!Set.of("DELIVERED","REJECTED_NO_EFFECT").contains(r.getString(3))||r.getBytes(2)==null)throw deny(409,"ACK_PREFIX_DENIED");
            Envelope parent=envelope(r.getBytes(1));outgoing(parent);Receipt parentAck=receipt(r.getBytes(2),parent);
            if(!parentAck.disposition().equals(r.getString(3).equals("DELIVERED")?"APPLIED":"REJECTED_NO_EFFECT"))throw deny(409,"ACK_PREFIX_DENIED");
            previous=parentAck.head();
          }
        }
      }
      if(!ack.head().equals(head(previous,stored,"APPLIED")))throw deny(409,"ACK_PREFIX_DENIED");
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET delivery_status='DELIVERED',ack_bytes=?,lease_token=NULL,lease_until=NULL,last_error=NULL WHERE message_id=? AND lease_token=? AND lease_until>clock_timestamp() AND delivery_status='PENDING'")){
        s.setBytes(1,ack.bytes());s.setObject(2,candidate.message());s.setObject(3,lease.token());return s.executeUpdate()==1;
      }
    });
  }
  public boolean failed(Lease lease,String error,boolean permanent,int jitterMillis){
    if(error==null||!error.matches("[A-Z][A-Z0-9_]{0,63}"))throw new IllegalArgumentException("Bounded error token required");
    long delay=retryDelayMillis(lease.attempt(),jitterMillis);
    return transaction(c->{
      environment(c,false);Envelope stored=outbox(c,lease.envelope().message());
      if(stored==null||!stored.digest().equals(envelope(lease.envelope().bytes()).digest()))throw deny(409,"MESSAGE_CONFLICT");outgoing(stored);
      try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_outbox SET delivery_status=CASE WHEN ? OR attempts=8 THEN 'PAUSED' ELSE 'PENDING' END,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond'),lease_token=NULL,lease_until=NULL,last_error=? WHERE message_id=? AND lease_token=? AND lease_until>clock_timestamp() AND delivery_status='PENDING'")){
        s.setBoolean(1,permanent);s.setLong(2,delay);s.setString(3,error);s.setObject(4,stored.message());s.setObject(5,lease.token());return s.executeUpdate()==1;
      }
    });
  }
  public static long retryDelayMillis(int attempt,int jitterMillis){
    if(attempt<1||attempt>8||jitterMillis<0||jitterMillis>250)throw new IllegalArgumentException("Retry budget exceeded");
    return Math.min(30_000L,1000L<<(attempt-1))+jitterMillis;
  }
  private void quarantine(Connection c,Envelope e,String reason)throws SQLException{
    if(!reason.matches("[A-Z][A-Z0-9_]{0,63}"))throw deny(500,"INVALID_POISON_REASON");
    try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_quarantine(message_id,operation_id,producer,stream_key,delivery_sequence,realm_id,producer_generation,participant_generation,envelope_digest,payload_digest,reason) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")){
      s.setObject(1,e.message());s.setObject(2,e.operation());s.setString(3,e.producer());s.setString(4,e.stream());s.setLong(5,e.sequence());s.setObject(6,e.realm());s.setObject(7,e.producerGeneration());s.setObject(8,e.participantGeneration());s.setString(9,e.digest());s.setString(10,e.payloadDigest());s.setString(11,reason);s.executeUpdate();
    }
    try(var s=c.prepareStatement("SELECT envelope_digest FROM "+SCHEMA+"delivery_quarantine WHERE message_id=?")){s.setObject(1,e.message());try(var r=s.executeQuery()){r.next();if(!e.digest().equals(r.getString(1)))throw deny(409,"MESSAGE_CONFLICT");}}
  }
  private void saveReceipt(Connection c,Envelope e,Receipt ack)throws SQLException{
    try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_inbox(message_id,operation_id,producer,stream_key,delivery_sequence,realm_id,producer_generation,participant_generation,message_type,phase,envelope_digest,payload_digest,disposition,receipt_head,ack_bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
      s.setObject(1,e.message());s.setObject(2,e.operation());s.setString(3,e.producer());s.setString(4,e.stream());s.setLong(5,e.sequence());s.setObject(6,e.realm());s.setObject(7,e.producerGeneration());s.setObject(8,e.participantGeneration());s.setString(9,e.type());s.setString(10,e.phase());s.setString(11,e.digest());s.setString(12,e.payloadDigest());s.setString(13,ack.disposition());s.setString(14,ack.head());s.setBytes(15,ack.bytes());s.executeUpdate();
    }
  }
  private void advance(Connection c,Envelope e,String head)throws SQLException{
    try(var s=c.prepareStatement("UPDATE "+SCHEMA+"delivery_receiver_stream SET last_sequence=last_sequence+1,receipt_head=? WHERE producer=? AND stream_key=? AND realm_id=? AND producer_generation=? AND participant_generation=? AND last_sequence=?")){
      s.setString(1,head);receiverKey(s,e,2);s.setLong(7,e.sequence()-1);if(s.executeUpdate()!=1)throw deny(409,"STREAM_GAP");
    }
  }
  private void lockProducer(Connection c,String consumer,String stream)throws SQLException{
    try(var s=c.prepareStatement("INSERT INTO "+SCHEMA+"delivery_producer_stream(consumer,stream_key,realm_id,producer_generation) VALUES (?,?,?,?) ON CONFLICT DO NOTHING")){producerKey(s,consumer,stream);s.executeUpdate();}
    try(var s=c.prepareStatement("SELECT next_sequence FROM "+SCHEMA+"delivery_producer_stream WHERE consumer=? AND stream_key=? AND realm_id=? AND producer_generation=? FOR UPDATE")){producerKey(s,consumer,stream);s.executeQuery().close();}
  }
  private Envelope outbox(Connection c,UUID message)throws SQLException{
    try(var s=c.prepareStatement("SELECT envelope_bytes,envelope_digest FROM "+SCHEMA+"delivery_outbox WHERE message_id=?")){s.setObject(1,message);try(var r=s.executeQuery()){if(!r.next())return null;Envelope e=envelope(r.getBytes(1));if(!e.digest().equals(r.getString(2)))throw deny(503,"STORED_DELIVERY_CORRUPT");return e;}}
  }
  private void environment(Connection c,boolean maintenance)throws SQLException{
    try(var s=c.createStatement();var r=s.executeQuery("SELECT realm_id,db_generation,identity_generation,content_generation,learning_generation,ready,maintenance FROM "+SCHEMA+"delivery_environment WHERE singleton FOR SHARE")){
      if(!r.next()||!realm.equals(r.getObject(1,UUID.class))||!generation.equals(r.getObject(2,UUID.class))||!peers.get("identity").equals(r.getObject(3,UUID.class))||!peers.get("content").equals(r.getObject(4,UUID.class))||!peers.get("learning").equals(r.getObject(5,UUID.class))||!r.getBoolean(6)||r.getBoolean(7)!=maintenance)throw deny(503,"DELIVERY_NOT_READY");
    }
  }
  private void incoming(Envelope e,String caller){
    if(!e.producer().equals(caller)||!e.consumer().equals(SERVICE)||e.producer().equals(SERVICE))throw deny(403,"DELIVERY_PEER_DENIED");
    if(!e.realm().equals(realm)||!e.participantGeneration().equals(generation)||!e.producerGeneration().equals(peer(caller)))throw deny(409,"DELIVERY_GENERATION_DENIED");
  }
  private void outgoing(Envelope e){
    if(!e.producer().equals(SERVICE)||!e.realm().equals(realm)||!e.producerGeneration().equals(generation)||!e.participantGeneration().equals(peer(e.consumer())))throw deny(409,"DELIVERY_GENERATION_DENIED");
  }
  private UUID peer(String name){UUID value=peers.get(name);if(value==null||name.equals(SERVICE))throw deny(403,"DELIVERY_PEER_DENIED");return value;}
  private void producerKey(PreparedStatement s,String consumer,String stream)throws SQLException{s.setString(1,consumer);s.setString(2,stream);s.setObject(3,realm);s.setObject(4,generation);}
  private void receiverKey(PreparedStatement s,Envelope e)throws SQLException{receiverKey(s,e,1);}
  private void receiverKey(PreparedStatement s,Envelope e,int start)throws SQLException{s.setString(start,e.producer());s.setString(start+1,e.stream());s.setObject(start+2,e.realm());s.setObject(start+3,e.producerGeneration());s.setObject(start+4,e.participantGeneration());}
  private static Instant time(Connection c)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SELECT clock_timestamp()")){r.next();return r.getObject(1,OffsetDateTime.class).toInstant();}}
  private static OffsetDateTime offset(Instant time){return time.atOffset(ZoneOffset.UTC);}
  /** Prevent accidental transaction ownership changes; not an untrusted-code sandbox. */
  static Connection local(Connection c){
    return (Connection)java.lang.reflect.Proxy.newProxyInstance(DeliveryStore.class.getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
      if(Set.of("commit","rollback","close","setAutoCommit","abort","unwrap","setSavepoint","releaseSavepoint").contains(method.getName()))throw new SQLException("Callback cannot own transaction");
      try{return method.invoke(c,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
    });
  }
  @FunctionalInterface private interface Sql<T>{T run(Connection c)throws SQLException;}
  private <T>T transaction(Sql<T> work){
    try(var c=source.getConnection()){
      c.setAutoCommit(false);
      try{
        try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='5000ms'; SET LOCAL statement_timeout='5000ms'; SET LOCAL transaction_timeout='6000ms'");}
        T result=work.run(c);c.commit();return result;
      }catch(Exception failure){try{c.rollback();}catch(SQLException ignored){}
        if(failure instanceof WireCodec.Denied denied)throw denied;
        if(failure instanceof SQLException sql&&"23505".equals(sql.getSQLState()))throw deny(409,"MESSAGE_CONFLICT");
        throw deny(503,"DELIVERY_UNAVAILABLE");
      }
    }catch(SQLException unavailable){throw deny(503,"DELIVERY_UNAVAILABLE");}
  }
}
