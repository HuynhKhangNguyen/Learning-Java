package lab.backend.learning.contentreplica;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict INT-04 codec; privileged state is derived by the store, never accepted from JSON. */
public final class PublicationWire {
  public static final int MAX_BYTES=8192;
  public static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
  private static final Set<String> FIELDS=Set.of("contractVersion","operationId","phase","parentRevision","targetRevision","bundleId","bundleSha256","manifestSha256","realmId","producerGeneration","participantGeneration","deliverySequence");
  private PublicationWire(){}
  public record Command(int contractVersion,UUID operationId,String phase,long parentRevision,long targetRevision,String bundleId,String bundleSha256,String manifestSha256,UUID realmId,UUID producerGeneration,UUID participantGeneration,long deliverySequence,String commandSha256){}
  public static Command command(byte[] input,UUID pathOperation,String pathPhase){
    try{
      if(input.length<2||input.length>MAX_BYTES)fail("PAYLOAD_SIZE_INVALID");
      StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input));
      JsonNode n=JSON.readTree(input);var names=new java.util.HashSet<String>();names.addAll(n.propertyNames());
      if(!n.isObject()||!names.equals(FIELDS)||n.path("contractVersion").asInt()!=1)fail("CONTRACT_UNSUPPORTED");
      UUID op=UUID.fromString(text(n,"operationId","[0-9a-f-]{36}"));String phase=text(n,"phase","STAGE|FENCE|RELEASE|ABORT");
      long parent=safe(n,"parentRevision"),target=safe(n,"targetRevision"),sequence=safe(n,"deliverySequence");
      if(!op.equals(pathOperation)||!phase.equals(pathPhase)||parent<1||target!=parent+1||sequence<1)fail("COMMAND_BINDING_INVALID");
      return new Command(1,op,phase,parent,target,text(n,"bundleId","[A-Za-z0-9][A-Za-z0-9._-]{0,127}"),text(n,"bundleSha256","[0-9a-f]{64}"),text(n,"manifestSha256","[0-9a-f]{64}"),UUID.fromString(text(n,"realmId",null)),UUID.fromString(text(n,"producerGeneration",null)),UUID.fromString(text(n,"participantGeneration",null)),sequence,sha256(input));
    }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("COMMAND_INVALID",e);}
  }
  private static long safe(JsonNode n,String f){JsonNode v=n.path(f);if(!v.isIntegralNumber()||!v.canConvertToLong()){fail("NUMBER_INVALID");}long x=v.asLong();if(x>9_007_199_254_740_991L)fail("NUMBER_INVALID");return x;}
  private static String text(JsonNode n,String f,String pattern){JsonNode v=n.path(f);if(!v.isString()||v.asString().isBlank()||(pattern!=null&&!v.asString().matches(pattern)))fail("FIELD_INVALID");return v.asString();}
  public static String sha256(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
  private static void fail(String code){throw new IllegalArgumentException(code);}
}
