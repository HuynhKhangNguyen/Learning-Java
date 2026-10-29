package lab.backend.content.identityaccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import lab.backend.content.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Own INT-01/02 wire adapter. No browser-supplied principal/deserialization path. */
public final class AdmissionWire {
  public static final String TOPOLOGY="r1-static-v1", ACTION="CONTENT_BODY_READ";
  public static final String FENCE_PATH="/internal/v1/session-fences/";
  static final Set<String> ADMISSION=Set.of("schemaVersion","decision","admissionId","requestNonce","provider","caller","audience","authRealmId","userId","sessionRef","sessionContextId","credentialEpoch","action","requestBinding","issuedAt","serverTime","validUntil","idleDeadline","absoluteDeadline","csrfVerified");
  static final Set<String> FENCE=Set.of("schemaVersion","operationId","scope","action","audience","userId","sessionRef","sessionContextId","epochFloor","authRealmId","topologyVersion","participantGeneration","commandDigest");

  /** Constructed by the endpoint after route/query/body validation, not from auth headers. */
  public static final class Request {
    final UUID nonce=UUID.randomUUID(),context;
    final String credential,path,binding;
    public Request(String credential,UUID context,String topicId,String unitId) {
      if(credential==null || !credential.matches("[A-Za-z0-9_-]{43}") || context==null)throw deny(401,"SESSION_INVALID");
      if(topicId==null || unitId==null || !topicId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}") || !unitId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}"))throw deny(400,"BAD_RESOURCE");
      this.credential=credential;this.context=context;
      this.path="/api/v1/topics/"+topicId+"/units/"+unitId;
      this.binding=sha("GET\n"+path+"\n\n"+sha(new byte[0]));
    }
    byte[] bytes() {
      return WireCodec.JSON.writeValueAsBytes(Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("action",ACTION),
          Map.entry("audience","content"),Map.entry("requestNonce",nonce.toString()),Map.entry("sessionCredential",credential),
          Map.entry("expectedContextId",context.toString()),Map.entry("method","GET"),Map.entry("canonicalPath",path),
          Map.entry("query",""),Map.entry("bodySha256",sha(new byte[0])),Map.entry("requestBinding",binding)));
    }
    @Override public String toString(){return "AdmissionRequest[credentials=REDACTED]";}
  }
  record Admission(UUID id,UUID nonce,UUID realm,UUID user,UUID session,UUID context,long epoch,
                   String binding,Instant issued,Instant serverTime,Instant until,Instant idle,Instant absolute) {}
  public record Fence(UUID operation,String scope,String action,UUID user,UUID session,UUID context,long floor,
                      UUID realm,UUID generation,String digest,String payloadDigest) {}
  public record Ack(UUID operation,String digest,UUID realm,UUID generation,String scope,long floor,UUID session,UUID context) {
    public Map<String,Object> json(){var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("receiver","content");
      m.put("operationId",operation.toString());m.put("commandDigest",digest);m.put("authRealmId",realm.toString());
      m.put("participantGeneration",generation.toString());m.put("topologyVersion",TOPOLOGY);m.put("scope",scope);
      m.put("appliedFloor",Long.toString(floor));m.put("sessionRef",session==null?null:session.toString());
      m.put("sessionContextId",context==null?null:context.toString());m.put("durable",true);return m;}
  }
  static Admission admission(byte[] bytes,Request request,UUID realm,Instant sentAt,Instant receivedAt) {
    JsonNode n=object(bytes,ADMISSION);
    if(!"ALLOW".equals(str(n,"decision")) || !"identity".equals(str(n,"provider")) || !"content".equals(str(n,"caller"))
        || !"content".equals(str(n,"audience")) || !ACTION.equals(str(n,"action")) || !n.get("csrfVerified").isBoolean())throw deny(403,"ADMISSION_BINDING_DENIED");
    Admission a=new Admission(id(n,"admissionId"),id(n,"requestNonce"),id(n,"authRealmId"),id(n,"userId"),id(n,"sessionRef"),
        id(n,"sessionContextId"),epoch(n,"credentialEpoch"),hex(n,"requestBinding"),time(n,"issuedAt"),time(n,"serverTime"),time(n,"validUntil"),time(n,"idleDeadline"),time(n,"absoluteDeadline"));
    if(!a.nonce.equals(request.nonce) || !a.realm.equals(realm) || !a.context.equals(request.context) || !a.binding.equals(request.binding))throw deny(403,"ADMISSION_BINDING_DENIED");
    // Use the measured request interval, not the maximum transport timeout as
    // permission for a fast response from an out-of-budget provider clock.
    if(receivedAt.isBefore(sentAt) || a.serverTime.isBefore(a.issued)
        || a.serverTime.isAfter(receivedAt.plusMillis(500)) || a.serverTime.isBefore(sentAt.minusMillis(500))
        || a.until.isAfter(a.issued.plusSeconds(15)) || a.until.isAfter(a.idle) || a.until.isAfter(a.absolute)
        || !a.until.isAfter(receivedAt.plusMillis(6500)))throw deny(403,"ADMISSION_TIME_DENIED");
    return a;
  }
  public static Fence fence(byte[] bytes,UUID operation) {
    JsonNode n=object(bytes,FENCE);String scope=str(n,"scope"),action=str(n,"action");
    UUID session=n.get("sessionRef").isNull()?null:id(n,"sessionRef"),context=n.get("sessionContextId").isNull()?null:id(n,"sessionContextId");
    if(!id(n,"operationId").equals(operation) || !"content".equals(str(n,"audience")) || !TOPOLOGY.equals(str(n,"topologyVersion")))throw deny(403,"FENCE_BINDING_DENIED");
    if(scope.equals("SESSION")) {if(session==null || context==null || !Set.of("LOGOUT","EXPIRY").contains(action))throw deny(400,"BAD_FENCE");}
    else if(scope.equals("ACCOUNT")) {if(session!=null || context!=null || !Set.of("RESET","LOGOUT_ALL","PASSWORD_CHANGE","DISABLE","ENABLE","RECOVERY").contains(action))throw deny(400,"BAD_FENCE");}
    else throw deny(400,"BAD_FENCE");
    UUID user=id(n,"userId"),realm=id(n,"authRealmId"),generation=id(n,"participantGeneration");long floor=epoch(n,"epochFloor");String digest=hex(n,"commandDigest");
    // Compatible with R04a's durable intent digest; envelope digest additionally binds context/generation/operation.
    String expected=sha(action+"\n"+user+"\n"+(session==null?"ACCOUNT":session)+"\n"+floor+"\n"+realm+"\n"+TOPOLOGY+"\ncontent,learning");
    if(!expected.equals(digest))throw deny(403,"COMMAND_DIGEST_DENIED");
    String payload=sha(operation+"\n"+scope+"\n"+digest+"\n"+context+"\n"+generation+"\ncontent");
    return new Fence(operation,scope,action,user,session,context,floor,realm,generation,digest,payload);
  }
  static JsonNode object(byte[] bytes,Set<String> expected){
    if(bytes.length>8192)throw deny(413,"PAYLOAD_TOO_LARGE");JsonNode n=WireCodec.strict(bytes);var names=new HashSet<String>();n.properties().forEach(p->names.add(p.getKey()));
    if(!names.equals(expected) || !n.get("schemaVersion").isInt() || n.get("schemaVersion").asInt()!=1)throw deny(400,"UNSUPPORTED_SCHEMA");return n;
  }
  static UUID id(JsonNode n,String key){return UUID.fromString(WireCodec.idempotencyKey(str(n,key)));}
  static long epoch(JsonNode n,String key){long v=Long.parseLong(WireCodec.decimal(n.get(key)));if(v<1)throw deny(400,"BAD_WIRE");return v;}
  static String str(JsonNode n,String key){return WireCodec.string(n,key);}
  static String hex(JsonNode n,String key){String s=str(n,key);if(!s.matches("[0-9a-f]{64}"))throw deny(400,"BAD_WIRE");return s;}
  static Instant time(JsonNode n,String key){return WireCodec.timestamp(str(n,key));}
  static String sha(String value){return sha(value.getBytes(StandardCharsets.UTF_8));}
  static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException("SHA256 unavailable");}}
  static WireCodec.Denied deny(int status,String code){return new WireCodec.Denied(status,code);}
  private AdmissionWire(){}
}
