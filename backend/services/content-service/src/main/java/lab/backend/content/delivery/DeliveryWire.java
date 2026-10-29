package lab.backend.content.delivery;

import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.*;
import lab.backend.content.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Exact stored bytes and peer-bound metadata; no domain/publication authority. */
public final class DeliveryWire {
  public static final String SERVICE="content", PATH="/internal/v1/delivery/messages/";
  public static final Set<String> SERVICES=Set.of("identity","content","learning");
  public static final int MAX_ENVELOPE=8192, MAX_PAYLOAD=4096;
  static final Set<String> ENVELOPE=Set.of("schemaVersion","messageId","operationId","correlationId","producer","consumer","environment","authRealmId","producerGeneration","participantGeneration","streamKey","deliverySequence","type","phase","payloadBytes","payloadDigest","createdAt");
  static final Set<String> ACK=Set.of("schemaVersion","receiver","producer","messageId","operationId","type","phase","streamKey","deliverySequence","authRealmId","producerGeneration","participantGeneration","envelopeDigest","payloadDigest","disposition","receiptHead","durable");
  static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  public record Envelope(int version,UUID message,UUID operation,UUID correlation,String producer,String consumer,
      UUID realm,UUID producerGeneration,UUID participantGeneration,String stream,long sequence,String type,String phase,
      byte[] payload,String payloadDigest,Instant createdAt,byte[] bytes,String digest) {
    public Envelope { payload=payload.clone();bytes=bytes.clone(); }
    @Override public byte[] payload(){return payload.clone();}
    @Override public byte[] bytes(){return bytes.clone();}
    public boolean payloadVerified(){return sha(payload).equals(payloadDigest);}
    @Override public String toString(){return "DeliveryEnvelope[message="+message+",payload=REDACTED]";}
  }
  public record Receipt(Envelope envelope,String disposition,String head,byte[] bytes) {
    public Receipt {bytes=bytes.clone();}
    @Override public byte[] bytes(){return bytes.clone();}
    @Override public String toString(){return "DeliveryReceipt[message="+envelope.message()+"]";}
  }
  public static Envelope create(UUID message,UUID operation,UUID correlation,String consumer,UUID realm,
      UUID producerGeneration,UUID participantGeneration,String stream,long sequence,String type,String phase,byte[] payload,Instant createdAt){
    if(payload==null||payload.length>MAX_PAYLOAD)throw deny(413,"PAYLOAD_TOO_LARGE");
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("messageId",message.toString());n.put("operationId",operation.toString());n.put("correlationId",correlation.toString());
    n.put("producer",SERVICE);n.put("consumer",consumer);n.put("environment","lab");n.put("authRealmId",realm.toString());n.put("producerGeneration",producerGeneration.toString());n.put("participantGeneration",participantGeneration.toString());
    n.put("streamKey",stream);n.put("deliverySequence",Long.toString(sequence));n.put("type",type);n.put("phase",phase);n.put("payloadBytes",Base64.getEncoder().encodeToString(payload));n.put("payloadDigest",sha(payload));n.put("createdAt",TIME.format(createdAt));
    return envelope(WireCodec.JSON.writeValueAsBytes(n));
  }
  /** Unknown major/type can be quarantined from validated metadata, never applied. */
  public static Envelope envelope(byte[] bytes){
    JsonNode n=object(bytes,ENVELOPE);int version=version(n);
    String producer=service(n,"producer"),consumer=service(n,"consumer");if(!"lab".equals(str(n,"environment")))throw deny(403,"ENVIRONMENT_DENIED");
    String stream=str(n,"streamKey"),type=str(n,"type"),phase=str(n,"phase");
    if(!stream.matches("[A-Za-z0-9:_-]{1,128}")||!type.matches("[A-Z][A-Z0-9_]{0,63}")||!phase.matches("[A-Z][A-Z0-9_]{0,63}"))throw deny(400,"BAD_DELIVERY");
    byte[] payload;String encoded=str(n,"payloadBytes");
    try{payload=Base64.getDecoder().decode(encoded);if(!Base64.getEncoder().encodeToString(payload).equals(encoded))throw new Exception();
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload));
    }catch(Exception invalid){throw deny(400,"BAD_PAYLOAD_ENCODING");}
    if(payload.length>MAX_PAYLOAD)throw deny(413,"PAYLOAD_TOO_LARGE");
    return new Envelope(version,id(n,"messageId"),id(n,"operationId"),id(n,"correlationId"),producer,consumer,id(n,"authRealmId"),id(n,"producerGeneration"),id(n,"participantGeneration"),stream,positive(n,"deliverySequence"),type,phase,payload,hex(n,"payloadDigest"),WireCodec.timestamp(str(n,"createdAt")),bytes,sha(bytes));
  }
  public static Receipt receipt(Envelope e,String disposition,String head){
    if(!Set.of("APPLIED","REJECTED_NO_EFFECT").contains(disposition)||!head.matches("[0-9a-f]{64}"))throw deny(400,"BAD_RECEIPT");
    var n=new LinkedHashMap<String,Object>();n.put("schemaVersion",1);n.put("receiver",e.consumer());n.put("producer",e.producer());n.put("messageId",e.message().toString());n.put("operationId",e.operation().toString());
    n.put("type",e.type());n.put("phase",e.phase());n.put("streamKey",e.stream());n.put("deliverySequence",Long.toString(e.sequence()));n.put("authRealmId",e.realm().toString());n.put("producerGeneration",e.producerGeneration().toString());n.put("participantGeneration",e.participantGeneration().toString());
    n.put("envelopeDigest",e.digest());n.put("payloadDigest",e.payloadDigest());n.put("disposition",disposition);n.put("receiptHead",head);n.put("durable",true);
    return new Receipt(e,disposition,head,WireCodec.JSON.writeValueAsBytes(n));
  }
  public static Receipt receipt(byte[] bytes,Envelope e){
    JsonNode n=object(bytes,ACK);if(version(n)!=1||!n.get("durable").isBoolean()||!n.get("durable").asBoolean()
      ||!str(n,"receiver").equals(e.consumer())||!str(n,"producer").equals(e.producer())||!id(n,"messageId").equals(e.message())||!id(n,"operationId").equals(e.operation())
      ||!str(n,"type").equals(e.type())||!str(n,"phase").equals(e.phase())||!str(n,"streamKey").equals(e.stream())||positive(n,"deliverySequence")!=e.sequence()
      ||!id(n,"authRealmId").equals(e.realm())||!id(n,"producerGeneration").equals(e.producerGeneration())||!id(n,"participantGeneration").equals(e.participantGeneration())
      ||!hex(n,"envelopeDigest").equals(e.digest())||!hex(n,"payloadDigest").equals(e.payloadDigest()))throw deny(409,"ACK_BINDING_DENIED");
    String disposition=str(n,"disposition");if(!Set.of("APPLIED","REJECTED_NO_EFFECT").contains(disposition))throw deny(409,"ACK_DISPOSITION_DENIED");
    return new Receipt(e,disposition,hex(n,"receiptHead"),bytes);
  }
  public static String head(String previous,Envelope e,String disposition){return sha(previous+"\n"+e.digest()+"\n"+disposition);}
  static JsonNode object(byte[] bytes,Set<String> keys){
    if(bytes.length>MAX_ENVELOPE)throw deny(413,"ENVELOPE_TOO_LARGE");var n=WireCodec.strict(bytes);var names=new HashSet<String>();n.properties().forEach(p->names.add(p.getKey()));
    if(!names.equals(keys))throw deny(400,"UNSUPPORTED_DELIVERY_SCHEMA");return n;
  }
  static int version(JsonNode n){if(!n.get("schemaVersion").isInt()||n.get("schemaVersion").asInt()<1)throw deny(400,"UNSUPPORTED_DELIVERY_SCHEMA");return n.get("schemaVersion").asInt();}
  static UUID id(JsonNode n,String key){return UUID.fromString(WireCodec.idempotencyKey(str(n,key)));}
  static long positive(JsonNode n,String key){long number=Long.parseLong(WireCodec.decimal(n.get(key)));if(number<1)throw deny(400,"BAD_DELIVERY_SEQUENCE");return number;}
  static String str(JsonNode n,String key){return WireCodec.string(n,key);}
  static String service(JsonNode n,String key){String value=str(n,key);if(!SERVICES.contains(value))throw deny(403,"DELIVERY_PEER_DENIED");return value;}
  static String hex(JsonNode n,String key){String value=str(n,key);if(!value.matches("[0-9a-f]{64}"))throw deny(400,"BAD_DELIVERY_DIGEST");return value;}
  static String sha(String text){return sha(text.getBytes(StandardCharsets.UTF_8));}
  static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException("SHA256 unavailable");}}
  static WireCodec.Denied deny(int status,String code){return new WireCodec.Denied(status,code);}
  private DeliveryWire(){}
}

