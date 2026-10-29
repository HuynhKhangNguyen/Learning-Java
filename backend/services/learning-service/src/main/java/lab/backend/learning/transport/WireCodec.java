package lab.backend.learning.transport;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Common wire rules owned here; no shared domain or client-supplied authority. */
public final class WireCodec {
  public static final int MAX_BYTES=65536;
  public static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20).maxStringLength(MAX_BYTES).build()).build()).build();
  private static final Set<String> FIELDS=Set.of("schemaVersion","action","audience","nonce","expectedRevision","issuedAt","validUntil","requestBinding");
  public record Probe(String action,String audience,String nonce,String revision,Instant issuedAt,Instant validUntil,String binding) {}
  public static final class Denied extends RuntimeException {
    public final int status; public final String code;
    public Denied(int status,String code){super(code);this.status=status;this.code=code;}
  }
  public static JsonNode strict(byte[] bytes) {
    if(bytes.length>MAX_BYTES)throw new Denied(413,"PAYLOAD_TOO_LARGE");
    try {
      String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
      JsonNode result=JSON.readTree(text);
      if(result==null || !result.isObject())throw new Denied(400,"BAD_WIRE");
      // A tree read must not silently accept a second JSON document.
      try(var parser=JSON.createParser(text)){while(parser.nextToken()!=null){} }
      try(var parser=JSON.createParser(text)){parser.nextToken();parser.skipChildren();if(parser.nextToken()!=null)throw new Denied(400,"BAD_WIRE");}
      unicode(result);return result;
    } catch(Denied e){throw e;} catch(Exception e){throw new Denied(400,"BAD_WIRE");}
  }
  private static void unicode(JsonNode node){
    if(node.isString())unicodeText(node.asString());
    if(node.isObject())for(var property:node.properties()){unicodeText(property.getKey());unicode(property.getValue());}
    if(node.isArray())for(JsonNode child:node)unicode(child);
  }
  private static void unicodeText(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length() || !Character.isLowSurrogate(s.charAt(i)))throw new Denied(400,"BAD_WIRE");}else if(Character.isLowSurrogate(c))throw new Denied(400,"BAD_WIRE");}}
  public static String decimal(JsonNode node){
    if(node==null || !node.isString() || !node.asString().matches("0|[1-9][0-9]{0,18}"))throw new Denied(400,"BAD_WIRE");
    try {Long.parseLong(node.asString());return node.asString();}catch(NumberFormatException e){throw new Denied(400,"BAD_WIRE");}
  }
  public static String string(JsonNode node,String field){JsonNode n=node.get(field);if(n==null || !n.isString())throw new Denied(400,"BAD_WIRE");return n.asString();}
  public static String idempotencyKey(String value){try{if(value==null || !UUID.fromString(value).toString().equals(value))throw new Exception();return value;}catch(Exception e){throw new Denied(400,"BAD_WIRE");}}
  public static String opaqueCursor(String value){if(value==null || value.isEmpty() || value.length()>1024 || !value.chars().allMatch(c->c>=33 && c<=126))throw new Denied(400,"BAD_WIRE");return value;}
  public static Instant timestamp(String text){
    if(!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"))throw new Denied(400,"BAD_WIRE");
    try{return Instant.parse(text);}catch(Exception e){throw new Denied(400,"BAD_WIRE");}
  }
  public static Probe probe(byte[] bytes){
    JsonNode n=strict(bytes);var names=new java.util.HashSet<String>();n.properties().forEach(p->names.add(p.getKey()));
    if(!names.equals(FIELDS) || !n.get("schemaVersion").isInt() || n.get("schemaVersion").asInt()!=1)throw new Denied(400,"UNSUPPORTED_SCHEMA");
    String nonce=string(n,"nonce");try{if(!UUID.fromString(nonce).toString().equals(nonce))throw new Exception();}catch(Exception e){throw new Denied(400,"BAD_WIRE");}
    String binding=string(n,"requestBinding");if(!binding.matches("[0-9a-f]{64}"))throw new Denied(400,"BAD_WIRE");
    Probe p=new Probe(string(n,"action"),string(n,"audience"),nonce,decimal(n.get("expectedRevision")),timestamp(string(n,"issuedAt")),timestamp(string(n,"validUntil")),binding);
    if(!MessageDigest.isEqual(binding.getBytes(StandardCharsets.US_ASCII),binding(p).getBytes(StandardCharsets.US_ASCII)))throw new Denied(403,"BINDING_DENIED");
    return p;
  }
  public static String binding(Probe p){
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("POST\n/internal/v1/transport/probes\n"+p.action()+"\n"+p.audience()+"\n"+p.nonce()+"\n"+p.revision()+"\n"+p.issuedAt()+"\n"+p.validUntil()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA256 unavailable");}
  }
  public static void window(Probe p,Instant dbNow){
    if(p.issuedAt().isAfter(dbNow.plusMillis(500)) || p.validUntil().isAfter(p.issuedAt().plusSeconds(15)) || !p.validUntil().isAfter(dbNow.plusMillis(6500)))throw new Denied(403,"TIME_BUDGET_DENIED");
  }
  public static Map<String,Object> error(String code){return Map.of("schemaVersion",1,"code",code,"traceId",UUID.randomUUID().toString(),"messageKey","transport.error."+code.toLowerCase(java.util.Locale.ROOT));}
  public static JsonNode operation(byte[] bytes){JsonNode n=strict(bytes);if(!n.has("schemaVersion") || !n.get("schemaVersion").isInt() || n.get("schemaVersion").asInt()!=1 || !n.has("state") || !Set.of("PENDING","COMPLETED","FAILED").contains(n.get("state").asString()))throw new Denied(503,"UNSUPPORTED_SCHEMA");return n;}
  private WireCodec(){}
}
