package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.SQLException;
import java.time.Instant;
import lab.backend.identity.transport.WireCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;

/** Servlet double kiểm boundary/dispatch/redaction. Caller argument do filter
 * cung cấp; đây không phải chứng minh certificate hoặc listener thật. */
class CanonicalAuthHttpHandlerTests {
  SessionAuthority sessions;ReauthAuthority reauth;CanonicalAuthHttpHandler handler;
  @BeforeEach void setup(){sessions=mock(SessionAuthority.class);reauth=mock(ReauthAuthority.class);handler=new CanonicalAuthHttpHandler(sessions,reauth,"https://lab.example");}
  MockHttpServletRequest request(String path){var r=new MockHttpServletRequest("POST",path);r.setSecure(true);r.setLocalPort(8443);r.setContentType("application/json");r.setContent(ReauthReservationWireTests.bytes(ReauthReservationWireTests.body()));return r;}
  MockHttpServletResponse call(MockHttpServletRequest request,String caller)throws Exception{var out=new MockHttpServletResponse();handler.handle(request,out,caller);assertThat(out.getHeader("Cache-Control")).isEqualTo("no-store");assertThat(out.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");assertThat(out.getHeaders("Set-Cookie")).isEmpty();return out;}
  void denied(MockHttpServletRequest r,String caller,int status)throws Exception{var out=call(r,caller);assertThat(out.getStatus()).isEqualTo(status);assertThat(out.getContentAsString()).doesNotContain("secret-sentinel",ReauthReservationWireTests.PROOF,"SQLException","password","csrfToken");assertThat(WireCodec.strict(out.getContentAsByteArray()).has("traceId")).isTrue();}
  @Test void int01PassesExactBodyOnlyToCanonicalAuthority()throws Exception{
    var r=request(CanonicalAdmissionWire.PATH);byte[] input="exact-authority-parser-input".getBytes(java.nio.charset.StandardCharsets.UTF_8);r.setContent(input);
    var now=Instant.parse("2026-10-09T00:00:00Z");var id=ReauthReservationWireTests.USER;
    when(sessions.admit(input,"content","https://lab.example")).thenReturn(new SessionAuthority.Admission(id,id,"content",id,id,id,id,1,"CONTENT_BODY_READ","b".repeat(64),now,now.plusSeconds(15),now.plusSeconds(1800),now.plusSeconds(43200),false));
    var out=call(r,"content");assertThat(out.getStatus()).isEqualTo(200);var body=WireCodec.strict(out.getContentAsByteArray());assertThat(body.get("decision").asString()).isEqualTo("ALLOW");assertThat(body.get("caller").asString()).isEqualTo("content");verify(sessions).admit(input,"content","https://lab.example");verifyNoInteractions(reauth);
  }
  @Test void int06ParsesBindingAndReturnsOnlyDurableReference()throws Exception{
    var input=ReauthReservationWire.parse(ReauthReservationWireTests.bytes(ReauthReservationWireTests.body()),"learning");var id=ReauthReservationWireTests.USER;
    when(reauth.reserve(input,"learning")).thenReturn(new ReauthAuthority.Reservation(id,id,input.user(),input.session(),input.context(),input.epoch(),input.realm(),input.purpose(),input.operationKey(),input.intentHash(),Instant.parse("2026-10-09T00:05:00Z")));
    var out=call(request(ReauthReservationWire.PATH),"learning");assertThat(out.getStatus()).isEqualTo(200);assertThat(WireCodec.strict(out.getContentAsByteArray()).get("state").asString()).isEqualTo("RESERVED");assertThat(out.getContentAsString()).doesNotContain(ReauthReservationWireTests.PROOF,"password","COMPLETED");verify(reauth).reserve(input,"learning");verifyNoInteractions(sessions);
  }
  @ParameterizedTest @ValueSource(strings={"identity","content","edge","browser",""})
  void int06OnlyLearningCallerBeforeBody(String caller)throws Exception{var r=request(ReauthReservationWire.PATH);r.setContent(new byte[9000]);denied(r,caller,403);verifyNoInteractions(sessions,reauth);}
  @ParameterizedTest @ValueSource(strings={"identity","edge","browser",""})
  void int01OnlyParticipantCaller(String caller)throws Exception{denied(request(CanonicalAdmissionWire.PATH),caller,403);verifyNoInteractions(sessions,reauth);}
  @ParameterizedTest @ValueSource(strings={"Origin","Cookie","X-Backend-Lab-Client-IP","X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto","X-CSRF-TOKEN","X-Session-Context","X-Auth-Flow","Idempotency-Key"})
  void browserAndSpoofHeadersDenyBeforeAuthority(String header)throws Exception{var r=request(ReauthReservationWire.PATH);r.addHeader(header,"secret-sentinel");denied(r,"learning",403);verifyNoInteractions(sessions,reauth);}
  @Test void publicPlaintextQueryAndWrongMethodCannotReachAuthority()throws Exception{
    var r=request(ReauthReservationWire.PATH);r.setLocalPort(8080);denied(r,"learning",403);
    r=request(ReauthReservationWire.PATH);r.setSecure(false);denied(r,"learning",403);
    r=request(ReauthReservationWire.PATH);r.setQueryString("proof=secret-sentinel");denied(r,"learning",400);
    r=request(ReauthReservationWire.PATH);r.setMethod("GET");denied(r,"learning",405);verifyNoInteractions(sessions,reauth);
  }
  @Test void unknownAndAmbiguousPathsAreNotCanonicalRoutes()throws Exception{
    denied(request(ReauthReservationWire.PATH+"/"),"learning",404);denied(request(ReauthReservationWire.PATH+"%2f"),"learning",400);
    assertThat(handler.handles("/internal/v1/transport/probes")).isFalse();verifyNoInteractions(sessions,reauth);
  }
  @ParameterizedTest @ValueSource(strings={"text/plain","application/json; charset=iso-8859-1","application/x-www-form-urlencoded"})
  void contentTypeIsExact(String content)throws Exception{var r=request(ReauthReservationWire.PATH);r.setContentType(content);denied(r,"learning",400);verifyNoInteractions(sessions,reauth);}
  @Test void duplicateContentTypeOversizeAndBadJsonDenyBeforeReserve()throws Exception{
    // Mock setContentType không tự tạo header enumeration; truyền hai values
    // rõ để fixture thực sự đại diện duplicate Content-Type trên wire.
    var r=request(ReauthReservationWire.PATH);r.addHeader("Content-Type",new String[]{"application/json","application/json"});denied(r,"learning",400);
    r=request(ReauthReservationWire.PATH);r.setContent(new byte[8193]);denied(r,"learning",413);
    r=request(ReauthReservationWire.PATH);r.setContent("{} {}".getBytes());denied(r,"learning",400);verifyNoInteractions(sessions,reauth);
  }
  @Test void dependencyFailureAndCanonicalDenyNeverEchoCredentials()throws Exception{
    when(reauth.reserve(any(),eq("learning"))).thenThrow(new SQLException("secret-sentinel"));denied(request(ReauthReservationWire.PATH),"learning",503);
    when(sessions.admit(any(),eq("learning"),any())).thenThrow(new WireCodec.Denied(401,"SESSION_INVALID"));denied(request(CanonicalAdmissionWire.PATH),"learning",401);
  }
  @Test void canonicalPrivatePortCannotAcceptLegacyOrArbitraryListener()throws Exception{
    handler=new CanonicalAuthHttpHandler(sessions,reauth,"https://lab.example",18441);
    denied(request(ReauthReservationWire.PATH),"learning",403);verifyNoInteractions(sessions,reauth);
    var r=request(ReauthReservationWire.PATH);r.setLocalPort(18441);r.setMethod("GET");denied(r,"learning",405);
    assertThatThrownBy(()->new CanonicalAuthHttpHandler(sessions,reauth,"https://lab.example",12345)).isInstanceOf(IllegalArgumentException.class);
  }
}
