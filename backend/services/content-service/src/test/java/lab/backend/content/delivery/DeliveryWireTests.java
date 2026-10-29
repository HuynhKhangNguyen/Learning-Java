package lab.backend.content.delivery;
import static org.assertj.core.api.Assertions.*;
import static lab.backend.content.delivery.DeliveryWire.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeliveryWireTests {
  static Envelope message(){return create(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"learning",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"content-global",9007199254740993L,"LAB_APPLY","APPLY","{\"delta\":1}".getBytes(StandardCharsets.UTF_8),Instant.parse("2026-10-07T00:00:00.123Z"));}
  static Map<String,Object> map(byte[] bytes){var result=new LinkedHashMap<String,Object>();WireCodec.strict(bytes).properties().forEach(p->{var n=p.getValue();result.put(p.getKey(),n.isString()?n.asString():n.isBoolean()?n.asBoolean():n.asInt());});return result;}
  @Test void exactPayloadVectorAndBigintWireAreIndependentOfJsonReserialization(){
    var e=message();assertThat(e.payloadDigest()).isEqualTo("2c5f28bf1e8d3e071e067f175a8b6f728081245b9b8313a9cce44a5aeff3f246");
    assertThat(e.sequence()).isEqualTo(9007199254740993L);assertThat(WireCodec.strict(e.bytes()).get("deliverySequence").asString()).isEqualTo("9007199254740993");
    assertThat(e.payloadVerified()).isTrue();assertThat(envelope(e.bytes()).bytes()).isEqualTo(e.bytes());
    assertThat(e.toString()).doesNotContain("{\"delta\":1}","eyJkZWx0YSI6MX0=");
  }
  @Test void payloadAndStoredEnvelopeAccessCannotMutateMessage(){
    var e=message();byte[] payload=e.payload(),wire=e.bytes();Arrays.fill(payload,(byte)0);Arrays.fill(wire,(byte)0);
    assertThat(e.payloadVerified()).isTrue();assertThat(envelope(e.bytes()).digest()).isEqualTo(e.digest());
  }
  @Test void futureMajorAndBadDigestRemainMetadataOnlyForQuarantine(){
    var m=map(message().bytes());m.put("schemaVersion",2);var future=envelope(WireCodec.JSON.writeValueAsBytes(m));assertThat(future.version()).isEqualTo(2);
    m.put("payloadDigest","0".repeat(64));assertThat(envelope(WireCodec.JSON.writeValueAsBytes(m)).payloadVerified()).isFalse();
  }
  @ParameterizedTest @ValueSource(strings={"numericSequence","zero","negative","overflow","leadingZero","badPeer","badEnvironment","badStream","unknown","duplicate","trailing","badBase64","invalidUtf8","oversizePayload","badTimestamp","nonIntegerVersion"})
  void invalidEnvelopeIsDeniedBeforeReceiverPolicy(String kind){
    var m=map(message().bytes());switch(kind){
      case "numericSequence"->m.put("deliverySequence",1);case "zero"->m.put("deliverySequence","0");case "negative"->m.put("deliverySequence","-1");case "overflow"->m.put("deliverySequence","9223372036854775808");case "leadingZero"->m.put("deliverySequence","01");
      case "badPeer"->m.put("producer","edge");case "badEnvironment"->m.put("environment","pilot");case "badStream"->m.put("streamKey","../escape");case "unknown"->m.put("owner","forged");
      case "badBase64"->m.put("payloadBytes","eyJkZWx0YSI6MX0");case "invalidUtf8"->m.put("payloadBytes",Base64.getEncoder().encodeToString(new byte[]{(byte)0xc3,0x28}));
      case "oversizePayload"->m.put("payloadBytes",Base64.getEncoder().encodeToString(new byte[4097]));case "badTimestamp"->m.put("createdAt","2026-10-07T00:00:00Z");case "nonIntegerVersion"->m.put("schemaVersion",1.0);default->{}
    }
    String raw=WireCodec.JSON.writeValueAsString(m);if(kind.equals("duplicate"))raw=raw.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1");if(kind.equals("trailing"))raw+="{}";byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(()->envelope(bytes)).isInstanceOf(WireCodec.Denied.class);
  }
  @Test void envelopeByteLimitIsStrict(){assertThatThrownBy(()->envelope(new byte[8193])).isInstanceOf(WireCodec.Denied.class).hasMessage("ENVELOPE_TOO_LARGE");}
  @Test void receiptPreservesExactBytesAndClonesOutput(){
    var e=message();String head=head("0".repeat(64),e,"APPLIED");var ack=receipt(e,"APPLIED",head);var decoded=receipt(ack.bytes(),e);assertThat(decoded.disposition()).isEqualTo("APPLIED");assertThat(decoded.head()).isEqualTo(head);
    byte[] original=ack.bytes();Arrays.fill(original,(byte)0);assertThat(receipt(ack.bytes(),e).bytes()).isEqualTo(ack.bytes());
  }
  @ParameterizedTest @ValueSource(strings={"receiver","producer","messageId","operationId","type","phase","streamKey","deliverySequence","authRealmId","producerGeneration","participantGeneration","envelopeDigest","payloadDigest","disposition","durable","schemaVersion","receiptHead"})
  void wrongAckCannotGrantDelivery(String field){
    var e=message();var m=map(receipt(e,"APPLIED",head("0".repeat(64),e,"APPLIED")).bytes());
    Object altered=switch(field){case "durable"->false;case "schemaVersion"->2;case "deliverySequence"->"1";case "envelopeDigest","payloadDigest"->"0".repeat(64);case "receiptHead"->"invalid";default->UUID.randomUUID().toString();};m.put(field,altered);
    assertThatThrownBy(()->receipt(WireCodec.JSON.writeValueAsBytes(m),e)).isInstanceOf(WireCodec.Denied.class);
  }
}

