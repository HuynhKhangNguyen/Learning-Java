package lab.backend.learning.identityaccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import lab.backend.learning.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Own INT-01/02 wire adapter. No browser-supplied principal/deserialization path. */
public final class AdmissionWire {
  public static final String TOPOLOGY="r1-static-v1", ACTION="LEARNING_DRAFT_SAVE";
  public static final String FENCE_PATH="/internal/v1/session-fences/";
  static final Set<String> ADMISSION=Set.of("schemaVersion","decision","admissionId","requestNonce","provider","caller","audience","authRealmId","userId","sessionRef","sessionContextId","credentialEpoch","action","requestBinding","issuedAt","serverTime","validUntil","idleDeadline","absoluteDeadline","csrfVerified");
  static final Set<String> FENCE=Set.of("schemaVersion","operationId","scope","action","audience","userId","sessionRef","sessionContextId","epochFloor","authRealmId","topologyVersion","participantGeneration","commandDigest");

  /** Constructed by the endpoint after route/query/body validation, not from auth headers. */
  public static final class Request {
    final UUID nonce = UUID.randomUUID();
    final UUID context;
    final UUID draft;
    final String credential;
    final String csrf;
    final String origin;
    final String path;
    final String bodySha;
    final String binding;
    final String action;
    final String method;
    final boolean mutation;

    /** Giữ constructor draft-save cũ; allocation phải dùng exact factory riêng. */
    public Request(String credential, UUID context, UUID draftId, String csrfToken,
        String origin, byte[] validatedBody) {
      this(credential, context, draftId, csrfToken, origin, validatedBody,
          ACTION, "PUT", "/api/v1/drafts/" + draftId, true);
      if (draftId == null) throw deny(400, "BAD_RESOURCE");
    }

    public static Request createAttempt(String credential, UUID context, String csrf,
        String origin, byte[] validatedBody) {
      return new Request(credential, context, null, csrf, origin, validatedBody,
          "LEARNING_ATTEMPT_CREATE", "POST", "/api/v1/attempts", true);
    }

    public static Request readAttempt(String credential, UUID context, UUID attempt) {
      if (attempt == null) throw deny(400, "BAD_RESOURCE");
      return new Request(credential, context, null, null, null, new byte[0],
          "LEARNING_ATTEMPT_READ", "GET", "/api/v1/attempts/" + attempt, false);
    }

    private Request(String credential, UUID context, UUID draft, String csrf, String origin,
        byte[] body, String action, String method, String path, boolean mutation) {
      if (credential == null || !credential.matches("[A-Za-z0-9_-]{43}") || context == null) {
        throw deny(401, "SESSION_INVALID");
      }
      if (body == null || body.length > 65536) throw deny(400, "BAD_RESOURCE");
      if (mutation) {
        if (csrf == null || !csrf.matches("[A-Za-z0-9_-]{43}")) {
          throw deny(403, "CSRF_DENIED");
        }
        if (origin == null || origin.length() > 256) throw deny(400, "BAD_ORIGIN");
        try {
          var uri = java.net.URI.create(origin);
          if (!"https".equals(uri.getScheme()) || uri.getHost() == null
              || uri.getRawUserInfo() != null || !"".equals(uri.getRawPath())
              || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException();
          }
        } catch (Exception invalid) {
          throw deny(400, "BAD_ORIGIN");
        }
      }
      this.credential = credential;
      this.context = context;
      this.draft = draft;
      this.csrf = csrf;
      this.origin = origin;
      this.action = action;
      this.method = method;
      this.path = path;
      this.mutation = mutation;
      this.bodySha = sha(body);
      this.binding = sha(method + "\n" + path + "\n\n" + bodySha);
    }

    byte[] bytes() {
      var value = new LinkedHashMap<String, Object>();
      value.put("schemaVersion", 1);
      value.put("action", action);
      value.put("audience", "learning");
      value.put("requestNonce", nonce.toString());
      value.put("sessionCredential", credential);
      value.put("expectedContextId", context.toString());
      value.put("method", method);
      value.put("canonicalPath", path);
      value.put("query", "");
      value.put("bodySha256", bodySha);
      value.put("requestBinding", binding);
      if (mutation) {
        value.put("csrfToken", csrf);
        value.put("origin", origin);
      }
      return WireCodec.JSON.writeValueAsBytes(value);
    }

    @Override
    public String toString() {
      return "AdmissionRequest[credentials=REDACTED]";
    }
  }
  record Admission(UUID id,UUID nonce,UUID realm,UUID user,UUID session,UUID context,long epoch,
                   String binding,Instant issued,Instant serverTime,Instant until,Instant idle,Instant absolute) {}
  public record Fence(UUID operation,String scope,String action,UUID user,UUID session,UUID context,long floor,
                      UUID realm,UUID generation,String digest,String payloadDigest) {}
  public record Ack(UUID operation,String digest,UUID realm,UUID generation,String scope,long floor,UUID session,UUID context) {
    public Map<String,Object> json(){var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",1);m.put("receiver","learning");
      m.put("operationId",operation.toString());m.put("commandDigest",digest);m.put("authRealmId",realm.toString());
      m.put("participantGeneration",generation.toString());m.put("topologyVersion",TOPOLOGY);m.put("scope",scope);
      m.put("appliedFloor",Long.toString(floor));m.put("sessionRef",session==null?null:session.toString());
      m.put("sessionContextId",context==null?null:context.toString());m.put("durable",true);return m;}
  }
  /** Kiểm exact action/read/mutation và thời gian của ALLOW mới, không cache principal. */
  static Admission admission(byte[] bytes, Request request, UUID realm,
      Instant sentAt, Instant receivedAt) {
    JsonNode node = object(bytes, ADMISSION);
    if (!"ALLOW".equals(str(node, "decision")) || !"identity".equals(str(node, "provider"))
        || !"learning".equals(str(node, "caller")) || !"learning".equals(str(node, "audience"))
        || !request.action.equals(str(node, "action"))
        || !node.get("csrfVerified").isBoolean()
        || node.get("csrfVerified").asBoolean() != request.mutation) {
      throw deny(403, "ADMISSION_BINDING_DENIED");
    }
    Admission admission = new Admission(
        id(node, "admissionId"), id(node, "requestNonce"), id(node, "authRealmId"),
        id(node, "userId"), id(node, "sessionRef"), id(node, "sessionContextId"),
        epoch(node, "credentialEpoch"), hex(node, "requestBinding"), time(node, "issuedAt"),
        time(node, "serverTime"), time(node, "validUntil"), time(node, "idleDeadline"),
        time(node, "absoluteDeadline"));
    if (!admission.nonce.equals(request.nonce) || !admission.realm.equals(realm)
        || !admission.context.equals(request.context) || !admission.binding.equals(request.binding)) {
      throw deny(403, "ADMISSION_BINDING_DENIED");
    }
    // Dùng khoảng request đo thực, không lấy transport timeout làm clock tolerance.
    if (receivedAt.isBefore(sentAt) || admission.serverTime.isBefore(admission.issued)
        || admission.serverTime.isAfter(receivedAt.plusMillis(500))
        || admission.serverTime.isBefore(sentAt.minusMillis(500))
        || admission.until.isAfter(admission.issued.plusSeconds(15))
        || admission.until.isAfter(admission.idle) || admission.until.isAfter(admission.absolute)
        || !admission.until.isAfter(receivedAt.plusMillis(6500))) {
      throw deny(403, "ADMISSION_TIME_DENIED");
    }
    return admission;
  }
  public static Fence fence(byte[] bytes,UUID operation) {
    JsonNode n=object(bytes,FENCE);String scope=str(n,"scope"),action=str(n,"action");
    UUID session=n.get("sessionRef").isNull()?null:id(n,"sessionRef"),context=n.get("sessionContextId").isNull()?null:id(n,"sessionContextId");
    if(!id(n,"operationId").equals(operation) || !"learning".equals(str(n,"audience")) || !TOPOLOGY.equals(str(n,"topologyVersion")))throw deny(403,"FENCE_BINDING_DENIED");
    if(scope.equals("SESSION")) {if(session==null || context==null || !Set.of("LOGOUT","EXPIRY").contains(action))throw deny(400,"BAD_FENCE");}
    else if(scope.equals("ACCOUNT")) {if(session!=null || context!=null || !Set.of("RESET","LOGOUT_ALL","PASSWORD_CHANGE","DISABLE","ENABLE","RECOVERY").contains(action))throw deny(400,"BAD_FENCE");}
    else throw deny(400,"BAD_FENCE");
    UUID user=id(n,"userId"),realm=id(n,"authRealmId"),generation=id(n,"participantGeneration");long floor=epoch(n,"epochFloor");String digest=hex(n,"commandDigest");
    // Compatible with R04a's durable intent digest; envelope digest additionally binds context/generation/operation.
    String expected=sha(action+"\n"+user+"\n"+(session==null?"ACCOUNT":session)+"\n"+floor+"\n"+realm+"\n"+TOPOLOGY+"\ncontent,learning");
    if(!expected.equals(digest))throw deny(403,"COMMAND_DIGEST_DENIED");
    String payload=sha(operation+"\n"+scope+"\n"+digest+"\n"+context+"\n"+generation+"\nlearning");
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
