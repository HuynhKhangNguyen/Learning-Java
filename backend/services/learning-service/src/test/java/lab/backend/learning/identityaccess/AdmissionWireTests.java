package lab.backend.learning.identityaccess;
import static org.assertj.core.api.Assertions.*;
import static lab.backend.learning.identityaccess.AdmissionWire.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lab.backend.learning.transport.WireCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Wire bindings và exact named Origin trước DB; không là runtime admission,
 * service trust hoặc browser proof. Những lớp đó cần actual qualifier riêng. */
class AdmissionWireTests {
  @Test void canonicalNamedBrowserOriginKeepsLegacyPolicy(){
    assertThat(AdmissionRuntime.allowedOrigin("https://127.0.0.1",false)).isEqualTo("https://127.0.0.1");
    assertThat(AdmissionRuntime.allowedOrigin("https://127.0.0.1",true)).isEqualTo("https://127.0.0.1");
    assertThat(AdmissionRuntime.allowedOrigin("https://localhost:17441",true)).isEqualTo("https://localhost:17441");
  }
  @Test void legacyProfileCannotEnableCanonicalBrowserOrigin(){
    // Profile nền không thể bật browser front chỉ bằng thay file config.
    assertThatThrownBy(()->AdmissionRuntime.allowedOrigin("https://localhost:17441",false)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->AdmissionRuntime.allowedOrigin(null,true)).isInstanceOf(IllegalArgumentException.class);
  }
  @ParameterizedTest @ValueSource(strings={"https://localhost:17442","https://localhost","https://localhost:17441/","https://127.0.0.1:17441","http://localhost:17441","https://wrong.example","https://localhost:17441?x=1"})
  void canonicalOriginCannotBeAnArbitraryEndpoint(String origin){
    assertThatThrownBy(()->AdmissionRuntime.allowedOrigin(origin,true)).isInstanceOf(IllegalArgumentException.class);
  }
  static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  static Map<String,Object> allow(Request r,UUID realm){Instant t=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);var n=new LinkedHashMap<String,Object>();
    n.put("schemaVersion",1);n.put("decision","ALLOW");n.put("admissionId",UUID.randomUUID().toString());n.put("requestNonce",r.nonce.toString());n.put("provider","identity");n.put("caller","learning");n.put("audience","learning");n.put("authRealmId",realm.toString());n.put("userId",UUID.randomUUID().toString());n.put("sessionRef",UUID.randomUUID().toString());n.put("sessionContextId",r.context.toString());n.put("credentialEpoch","1");n.put("action",ACTION);n.put("requestBinding",r.binding);
    n.put("issuedAt",FORMAT.format(t));n.put("serverTime",FORMAT.format(t));n.put("validUntil",FORMAT.format(t.plusSeconds(15)));n.put("idleDeadline",FORMAT.format(t.plusSeconds(1800)));n.put("absoluteDeadline",FORMAT.format(t.plusSeconds(43200)));n.put("csrfVerified",true);return n;
  }
  @Test void mutationRequestBindsBodyOriginCsrfAndDoesNotPrintSecrets(){var r=AdmissionGateIT.request();assertThat(r.toString()).doesNotContain(r.credential,r.csrf);var n=WireCodec.strict(r.bytes());assertThat(n.get("method").asString()).isEqualTo("PUT");assertThat(n.get("canonicalPath").asString()).isEqualTo("/api/v1/drafts/"+r.draft);assertThat(n.get("origin").asString()).isEqualTo("https://127.0.0.1");assertThat(n.get("csrfToken").asString()).isEqualTo(r.csrf);assertThat(n.get("requestBinding").asString()).isEqualTo(sha("PUT\n"+r.path+"\n\n"+r.bodySha));}
  @Test void invalidCsrfOriginOrOversizeBodyIsDenied(){
    assertThatThrownBy(()->new Request("a".repeat(43),UUID.randomUUID(),UUID.randomUUID(),"short","https://127.0.0.1",new byte[0])).isInstanceOf(WireCodec.Denied.class);
    assertThatThrownBy(()->new Request("a".repeat(43),UUID.randomUUID(),UUID.randomUUID(),"c".repeat(43),"http://127.0.0.1",new byte[0])).isInstanceOf(WireCodec.Denied.class);
    assertThatThrownBy(()->new Request("a".repeat(43),UUID.randomUUID(),UUID.randomUUID(),"c".repeat(43),"https://127.0.0.1",new byte[65537])).isInstanceOf(WireCodec.Denied.class);
  }
  @Test void boundAdmissionAcceptsBigintStringsWithoutLoss(){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);m.put("credentialEpoch","9007199254740993");Instant sample=Instant.parse(m.get("serverTime").toString());assertThat(admission(WireCodec.JSON.writeValueAsBytes(m),r,realm,sample,sample).epoch()).isEqualTo(9007199254740993L);}
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
  @ParameterizedTest @ValueSource(strings={"provider","caller","audience","action","requestNonce","sessionContextId","requestBinding","authRealmId","decision","csrfVerified"})
  void changedBindingIsDenied(String key){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var n=allow(r,realm);n.put(key,key.equals("requestBinding")?"0".repeat(64):key.equals("csrfVerified")?false:UUID.randomUUID().toString());assertThatThrownBy(()->admission(WireCodec.JSON.writeValueAsBytes(n),r,realm,Instant.now(),Instant.now())).isInstanceOf(WireCodec.Denied.class);}
  @ParameterizedTest @ValueSource(strings={"numericEpoch","overflow","zero","unknown","duplicate","trailing","expired","future","overlong","idle","badBoolean"})
  void malformedOrExpiredAdmissionDenied(String kind){var r=AdmissionGateIT.request();UUID realm=UUID.randomUUID();var m=allow(r,realm);
    switch(kind){case "numericEpoch"->m.put("credentialEpoch",1);case "overflow"->m.put("credentialEpoch","9223372036854775808");case "zero"->m.put("credentialEpoch","0");case "unknown"->m.put("role","admin");case "expired"->m.put("validUntil",FORMAT.format(Instant.now()));case "future"->m.put("serverTime",FORMAT.format(Instant.now().plusSeconds(2)));case "overlong"->m.put("validUntil",FORMAT.format(Instant.now().plusSeconds(16)));case "idle"->m.put("idleDeadline",FORMAT.format(Instant.now()));case "badBoolean"->m.put("csrfVerified","true");default->{}}
    String raw=WireCodec.JSON.writeValueAsString(m);if(kind.equals("duplicate"))raw=raw.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1");if(kind.equals("trailing"))raw+="{}";byte[] bytes=raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThatThrownBy(()->admission(bytes,r,realm,Instant.now(),Instant.now())).isInstanceOf(WireCodec.Denied.class);
  }
}
