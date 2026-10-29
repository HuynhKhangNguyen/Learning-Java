package lab.backend.content.identityaccess;
import static org.assertj.core.api.Assertions.*;
import static lab.backend.content.identityaccess.AdmissionWire.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdmissionWireTests {
  static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  static Map<String,Object> allow(Request r,UUID realm){Instant t=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);var n=new LinkedHashMap<String,Object>();
    n.put("schemaVersion",1);n.put("decision","ALLOW");n.put("admissionId",UUID.randomUUID().toString());n.put("requestNonce",r.nonce.toString());n.put("provider","identity");n.put("caller","content");n.put("audience","content");n.put("authRealmId",realm.toString());n.put("userId",UUID.randomUUID().toString());n.put("sessionRef",UUID.randomUUID().toString());n.put("sessionContextId",r.context.toString());n.put("credentialEpoch","1");n.put("action",ACTION);n.put("requestBinding",r.binding);
    n.put("issuedAt",FORMAT.format(t));n.put("serverTime",FORMAT.format(t));n.put("validUntil",FORMAT.format(t.plusSeconds(15)));n.put("idleDeadline",FORMAT.format(t.plusSeconds(1800)));n.put("absoluteDeadline",FORMAT.format(t.plusSeconds(43200)));n.put("csrfVerified",false);return n;
  }
  @Test void requestIsServerBoundAndDoesNotPrintCredential(){var r=AdmissionGateIT.request();assertThat(r.toString()).doesNotContain(r.credential);var n=WireCodec.strict(r.bytes());assertThat(n.get("requestBinding").asString()).isEqualTo(sha("GET\n/api/v1/topics/CL-T01/units/CL-U01\n\n"+sha(new byte[0])));}
  @Test void boundAdmissionAcceptsBigintStringsWithoutLoss(){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);m.put("credentialEpoch","9007199254740993");assertThat(admission(WireCodec.JSON.writeValueAsBytes(m),r,realm,Instant.now(),Instant.now()).epoch()).isEqualTo(9007199254740993L);}
  @Test void measuredClockWindowRejectsPastPeerButAllowsActualNetworkDelay(){
    var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);
    Instant received=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),issued=received.minusSeconds(2);
    m.put("issuedAt",FORMAT.format(issued));m.put("serverTime",FORMAT.format(issued));m.put("validUntil",FORMAT.format(issued.plusSeconds(15)));
    byte[] body=WireCodec.JSON.writeValueAsBytes(m);
    assertThatThrownBy(()->admission(body,r,realm,received.minusMillis(10),received)).isInstanceOf(WireCodec.Denied.class).hasMessage("ADMISSION_TIME_DENIED");
    assertThat(admission(body,r,realm,received.minusMillis(1900),received).serverTime()).isEqualTo(issued);
  }
  @Test void backwardLocalClockIntervalCannotAuthorize(){
    var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);Instant sent=Instant.now();
    assertThatThrownBy(()->admission(WireCodec.JSON.writeValueAsBytes(m),r,realm,sent,sent.minusMillis(1))).isInstanceOf(WireCodec.Denied.class).hasMessage("ADMISSION_TIME_DENIED");
  }
  @ParameterizedTest @ValueSource(strings={"provider","caller","audience","action","requestNonce","sessionContextId","requestBinding","authRealmId","decision"})
  void changedBindingIsDenied(String key){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var n=allow(r,realm);n.put(key,key.equals("requestBinding")?"0".repeat(64):UUID.randomUUID().toString());assertThatThrownBy(()->admission(WireCodec.JSON.writeValueAsBytes(n),r,realm,Instant.now(),Instant.now())).isInstanceOf(WireCodec.Denied.class);}
  @ParameterizedTest @ValueSource(strings={"numericEpoch","overflow","zero","unknown","duplicate","trailing","expired","future","overlong","idle","badBoolean"})
  void malformedOrExpiredAdmissionDenied(String kind){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);
    switch(kind){case "numericEpoch"->m.put("credentialEpoch",1);case "overflow"->m.put("credentialEpoch","9223372036854775808");case "zero"->m.put("credentialEpoch","0");case "unknown"->m.put("role","admin");case "expired"->m.put("validUntil",FORMAT.format(Instant.now()));case "future"->m.put("serverTime",FORMAT.format(Instant.now().plusSeconds(2)));case "overlong"->m.put("validUntil",FORMAT.format(Instant.now().plusSeconds(16)));case "idle"->m.put("idleDeadline",FORMAT.format(Instant.now()));case "badBoolean"->m.put("csrfVerified","true");default->{}}
    String raw=WireCodec.JSON.writeValueAsString(m);if(kind.equals("duplicate"))raw=raw.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1");if(kind.equals("trailing"))raw+="{}";byte[] bytes=raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThatThrownBy(()->admission(bytes,r,realm,Instant.now(),Instant.now())).isInstanceOf(WireCodec.Denied.class);
  }
}
