package lab.backend.content;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import lab.backend.content.transport.WireCodec;

class WireContractIT {
  private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
  @ParameterizedTest @ValueSource(strings={"0","9007199254740991","9007199254740992","9007199254740993","9223372036854775807"})
  void WIRE01ExactDecimalStringRoundtrip(String s){assertThat(WireCodec.decimal(WireCodec.JSON.readTree("\""+s+"\""))).isEqualTo(s);}
  @ParameterizedTest @ValueSource(strings={"-1","01","1e3","1.0","9223372036854775808","99999999999999999999"})
  void WIRE01BadCounterRejected(String s){assertThatThrownBy(()->WireCodec.decimal(WireCodec.JSON.readTree("\""+s+"\""))).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE01NumberDoesNotCoerceToString(){assertThatThrownBy(()->WireCodec.decimal(WireCodec.JSON.readTree("9007199254740993"))).isInstanceOf(WireCodec.Denied.class);}
  @ParameterizedTest @ValueSource(strings={"{\"x\":1,\"x\":2}","{\"x\":1,\"\\u0078\":2}","{\"x\":\"\\ud800\"}","{\"x\":\"\\udc00\"}","{} {}"})
  void WIRE02StrictJSONRejectsAmbiguity(String s){assertThatThrownBy(()->WireCodec.strict(bytes(s))).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE02InvalidUTF8Rejected(){assertThatThrownBy(()->WireCodec.strict(new byte[]{123,34,120,34,58,34,(byte)0xc3,40,34,125})).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE02DepthAndSizeBounded(){assertThatThrownBy(()->WireCodec.strict(bytes("{\"x\":"+"[".repeat(21)+"0"+"]".repeat(21)+"}"))).isInstanceOf(WireCodec.Denied.class);assertThatThrownBy(()->WireCodec.strict(new byte[65537])).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE02ExternalBindingVectorAndUnknownOwner()throws Exception{
    byte[] fixture;try(var stream=getClass().getResourceAsStream("/contracts/common/probe-example.json")){fixture=stream.readAllBytes();}
    assertThat(WireCodec.probe(fixture).revision()).isEqualTo("9007199254740993");
    String extra=new String(fixture,StandardCharsets.UTF_8).replace("\"schemaVersion\": 1","\"schemaVersion\": 1, \"owner\": \"forged\"");
    assertThatThrownBy(()->WireCodec.probe(bytes(extra))).isInstanceOf(WireCodec.Denied.class);
  }
  @Test void WIRE03ErrorsAreVersionedAndNeverEchoPayload(){var e=WireCodec.error("ACTION_DENIED");assertThat(e.keySet()).containsExactlyInAnyOrder("schemaVersion","code","traceId","messageKey");UUID.fromString((String)e.get("traceId"));assertThat(e).doesNotContainKeys("answer","cookie","sql","stack","password");}
  @Test void WIRE03PendingRemainsPending(){assertThat(WireCodec.operation(bytes("{\"schemaVersion\":1,\"state\":\"PENDING\"}")).get("state").asString()).isEqualTo("PENDING");}
  @Test void WIRE04KeyShapeIsStrictWithoutReceiptAuthority(){assertThat(WireCodec.idempotencyKey("83e52102-3236-4ca9-bdfd-01e0b3d18cfa")).isEqualTo("83e52102-3236-4ca9-bdfd-01e0b3d18cfa");for(String key:new String[]{"not-a-key","83e52102-3236-4ca9-bdfd-01e0b3d18cfa ","1-1-1-1-1"})assertThatThrownBy(()->WireCodec.idempotencyKey(key)).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE05OpaqueCursorShapeCannotGrantAuthority(){assertThat(WireCodec.opaqueCursor("sealed.cursor_opaque-123")).isEqualTo("sealed.cursor_opaque-123");for(String cursor:new String[]{"","x".repeat(1025),"has whitespace","has\\nnewline".replace("\\n","\n")})assertThatThrownBy(()->WireCodec.opaqueCursor(cursor)).isInstanceOf(WireCodec.Denied.class);}
  @Test void WIRE06AdditiveOptionalResponseAccepted(){assertThat(WireCodec.operation(bytes("{\"schemaVersion\":1,\"state\":\"PENDING\",\"newOptional\":true}")).get("state").asString()).isEqualTo("PENDING");}
  @ParameterizedTest @ValueSource(strings={"{\"schemaVersion\":2,\"state\":\"COMPLETED\"}","{\"schemaVersion\":1,\"state\":\"NEW_STATE\"}","{\"schemaVersion\":\"1\",\"state\":\"PENDING\"}"})
  void WIRE06UnknownMajorOrStateFailsClosed(String s){assertThatThrownBy(()->WireCodec.operation(bytes(s))).isInstanceOf(WireCodec.Denied.class);}
  @Test void DeadlineBoundaryUsesConservativeClockBudget(){var now=java.time.Instant.parse("2026-10-02T00:00:00Z");var safe=new WireCodec.Probe("a","b","c","0",now,now.plusMillis(6501),"x");assertThatCode(()->WireCodec.window(safe,now)).doesNotThrowAnyException();var cutoff=new WireCodec.Probe("a","b","c","0",now,now.plusMillis(6500),"x");assertThatThrownBy(()->WireCodec.window(cutoff,now)).isInstanceOf(WireCodec.Denied.class);var drift=new WireCodec.Probe("a","b","c","0",now.plusMillis(501),now.plusSeconds(15),"x");assertThatThrownBy(()->WireCodec.window(drift,now)).isInstanceOf(WireCodec.Denied.class);}
  private tools.jackson.databind.JsonNode registry(){try(var stream=getClass().getResourceAsStream("/contracts/common/registry.json")){return WireCodec.JSON.readTree(stream);}catch(Exception e){throw new IllegalStateException(e);}}
}
