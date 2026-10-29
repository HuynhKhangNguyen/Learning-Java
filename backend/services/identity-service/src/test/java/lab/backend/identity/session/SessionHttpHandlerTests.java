package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lab.backend.identity.credential.CredentialWorkflow.Purpose;
import lab.backend.identity.transport.WireCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Kiểm servlet boundary bằng doubles, không thay DB/TLS/browser evidence. */
class SessionHttpHandlerTests {
  static final String ORIGIN="https://lab.example",COOKIE="a".repeat(43),CSRF="b".repeat(43);
  static final UUID CONTEXT=UUID.randomUUID(),USER=UUID.randomUUID(),OP=UUID.randomUUID(),KEY=UUID.randomUUID();
  SessionAuthority authority;SessionHttpHandler handler;
  @BeforeEach void setup(){authority=mock(SessionAuthority.class);handler=new SessionHttpHandler(authority,ORIGIN);}
  MockHttpServletRequest request(String route,Map<String,Object> body)throws Exception {
    var req=new MockHttpServletRequest(body==null?"GET":"POST","/api/v1/"+route);req.setSecure(true);req.setLocalPort(8080);
    req.addHeader("Cookie",SessionAuthority.COOKIE+"="+COOKIE);req.addHeader("X-CSRF-TOKEN",CSRF);req.addHeader("Origin",ORIGIN);req.addHeader("Idempotency-Key",KEY.toString());
    if(body!=null){req.setContentType("application/json");req.setContent(WireCodec.JSON.writeValueAsBytes(body));}return req;
  }
  Map<String,Object> loginBody(){return Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"loginName","learner","password","valid password value");}
  MockHttpServletResponse call(MockHttpServletRequest req)throws Exception {var response=new MockHttpServletResponse();handler.handle(req,response,"edge","127.0.0.1");assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");return response;}
  SessionAuthority.View active(){return new SessionAuthority.View("ACTIVE",CONTEXT,CSRF,USER,"9007199254740993",null);}
  void denied(MockHttpServletRequest req,int status)throws Exception {var response=call(req);assertThat(response.getStatus()).isEqualTo(status);assertThat(response.getHeaders("Set-Cookie")).isEmpty();var json=WireCodec.strict(response.getContentAsByteArray());assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);UUID.fromString(json.get("traceId").asString());assertThat(response.getContentAsString()).doesNotContain("secret-sentinel",COOKIE,CSRF,"SQLException","stackTrace");}

  @Test void onlyExplicitCredentialBootstrapIssuesCookie()throws Exception {
    when(authority.bootstrap(COOKIE)).thenReturn(active());var ordinary=call(request("session",null));assertThat(ordinary.getStatus()).isEqualTo(200);assertThat(ordinary.getHeaders("Set-Cookie")).isEmpty();
    when(authority.begin(COOKIE)).thenReturn(new SessionAuthority.Issued(COOKIE,new SessionAuthority.View("ANONYMOUS",CONTEXT,CSRF,null,null,null),900));
    var explicit=request("session",Map.of("schemaVersion",1));explicit.addHeader("X-Auth-Flow","credential");String cookie=call(explicit).getHeader("Set-Cookie");
    assertThat(cookie).startsWith(SessionAuthority.COOKIE+"="+COOKIE+";").contains("Path=/","HttpOnly","Secure","SameSite=Lax","Max-Age=","Expires=").doesNotContain("Domain=");
    when(authority.begin(COOKIE)).thenReturn(new SessionAuthority.Issued(null,new SessionAuthority.View("ANONYMOUS",CONTEXT,CSRF,null,null,null),0));
    var retry=request("session",Map.of("schemaVersion",1));retry.addHeader("X-Auth-Flow","credential");assertThat(call(retry).getHeaders("Set-Cookie")).isEmpty();
  }
  @Test void loginCookieComesOnlyFromCommittedAuthorityResult()throws Exception {
    when(authority.login(COOKIE,CONTEXT,CSRF,"learner","valid password value","127.0.0.1")).thenReturn(new SessionAuthority.Issued("c".repeat(43),active(),43200));
    var response=call(request("auth/login",loginBody()));assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeaders("Set-Cookie")).hasSize(1);
    assertThat(WireCodec.strict(response.getContentAsByteArray()).get("credentialEpoch").asString()).isEqualTo("9007199254740993");
    when(authority.login(COOKIE,CONTEXT,CSRF,"learner","valid password value","127.0.0.1")).thenThrow(new SQLException("secret-sentinel"));denied(request("auth/login",loginBody()),503);
  }
  @Test void delayedExpiredIssuanceCannotEmitACookieDeletion()throws Exception {
    when(authority.begin(COOKIE)).thenReturn(new SessionAuthority.Issued("c".repeat(43),active(),900,Instant.now().minusSeconds(1)));
    var req=request("session",Map.of("schemaVersion",1));req.addHeader("X-Auth-Flow","credential");denied(req,503);
  }
  @Test void getCannotMintAndExplicitPostRequiresBootstrapSchema()throws Exception {
    var get=request("session",null);get.addHeader("X-Auth-Flow","credential");denied(get,405);
    var post=request("session",Map.of("schemaVersion",1));denied(post,405);
    var forged=request("session",Map.of("schemaVersion",1,"owner","secret-sentinel"));forged.addHeader("X-Auth-Flow","credential");denied(forged,400);verifyNoInteractions(authority);
  }
  @Test void logoutAndStatusNeverSetCookieOrExposePrincipal()throws Exception {
    when(authority.logout(COOKIE,CONTEXT,CSRF,KEY)).thenReturn(OP);
    var logout=call(request("auth/logout",Map.of("schemaVersion",1,"expectedContextId",CONTEXT)));assertThat(logout.getStatus()).isEqualTo(202);assertThat(logout.getHeaders("Set-Cookie")).isEmpty();
    when(authority.operationStatus(COOKIE,CONTEXT,OP)).thenReturn("PENDING_FENCES");
    var status=request("auth/operations/"+OP,null);status.addHeader("X-Session-Context",CONTEXT.toString());var response=call(status);
    assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeaders("Set-Cookie")).isEmpty();assertThat(response.getContentAsString()).doesNotContain("userId","csrfToken","credentialEpoch");
  }
  @Test void pendingBootstrapKeepsLockedStateAndDoesNotRotateCookie()throws Exception {
    when(authority.bootstrap(COOKIE)).thenReturn(new SessionAuthority.View("REVOKING",CONTEXT,null,null,null,OP));
    var response=call(request("session",null));var body=WireCodec.strict(response.getContentAsByteArray());assertThat(body.get("state").asString()).isEqualTo("REVOKING");assertThat(body.get("operationId").asString()).isEqualTo(OP.toString());assertThat(response.getHeaders("Set-Cookie")).isEmpty();
  }
  @Test void activateAndResetNeverAutoLoginOrIssueCookie()throws Exception {
    String token="d".repeat(43),password="valid password value";var body=Map.<String,Object>of("schemaVersion",1,"expectedContextId",CONTEXT,"token",token,"newPassword",password);
    when(authority.consume(Purpose.ACTIVATE,COOKIE,CONTEXT,CSRF,token,password,"127.0.0.1",null)).thenReturn(new SessionAuthority.Consumed("ACTIVATED",null));
    when(authority.consume(Purpose.RESET,COOKIE,CONTEXT,CSRF,token,password,"127.0.0.1",KEY)).thenReturn(new SessionAuthority.Consumed("PENDING_FENCES",OP));
    var activation=call(request("auth/activate",body));assertThat(activation.getStatus()).isEqualTo(200);assertThat(activation.getHeaders("Set-Cookie")).isEmpty();
    var reset=call(request("auth/password-reset",body));assertThat(reset.getStatus()).isEqualTo(202);assertThat(reset.getHeaders("Set-Cookie")).isEmpty();
    verify(authority,never()).login(any(),any(),any(),any(),any(),any());
  }
  @ParameterizedTest @ValueSource(strings={"auth/logout-all","auth/password-change"})
  void reauthCapabilityIsExplicitlyUnavailableWithoutPasswordFallback(String path)throws Exception {
    when(authority.bootstrap(COOKIE)).thenReturn(active());var body=new java.util.HashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"reauthProof","secret-sentinel"));
    if(path.endsWith("password-change"))body.put("newPassword","valid password value");denied(request(path,body),503);
    verify(authority,never()).interactive(any(),any(),any());verify(authority,never()).login(any(),any(),any(),any(),any(),any());
    verify(authority,never()).consume(any(),any(),any(),any(),any(),any(),any(),any());
  }
  @ParameterizedTest @ValueSource(strings={"Origin","X-CSRF-TOKEN","Idempotency-Key"})
  void duplicateSensitiveHeadersAreRejectedBeforeAuthority(String header)throws Exception {var req=request("auth/login",loginBody());req.addHeader(header,"secret-sentinel");denied(req,400);verifyNoInteractions(authority);}
  @ParameterizedTest @ValueSource(strings={"https://evil.example","null","https://lab.example/"})
  void originMustBeExact(String supplied)throws Exception {var req=request("auth/login",loginBody());req.removeHeader("Origin");req.addHeader("Origin",supplied);denied(req,403);verifyNoInteractions(authority);}
  @Test void mutationAndExplicitPreauthRequireOrigin()throws Exception {var mutation=request("auth/login",loginBody());mutation.removeHeader("Origin");denied(mutation,403);var preauth=request("session",null);preauth.removeHeader("Origin");preauth.addHeader("X-Auth-Flow","credential");denied(preauth,403);verifyNoInteractions(authority);}
  @Test void duplicateAndQuotedAuthCookiesAreRejected()throws Exception {var req=request("auth/login",loginBody());req.addHeader("Cookie",SessionAuthority.COOKIE+"="+COOKIE);denied(req,400);var quoted=request("auth/login",loginBody());quoted.removeHeader("Cookie");quoted.addHeader("Cookie",SessionAuthority.COOKIE+"=\""+COOKIE+"\"");denied(quoted,400);verifyNoInteractions(authority);}
  @ParameterizedTest @ValueSource(strings={"owner","role","passed","interactive"})
  void privilegedUnknownFieldsAreRejected(String field)throws Exception {var body=new java.util.HashMap<>(loginBody());body.put(field,"secret-sentinel");denied(request("auth/login",body),400);verifyNoInteractions(authority);}
  @ParameterizedTest @ValueSource(strings={"text/plain","application/json; charset=iso-8859-1","application/x-www-form-urlencoded"})
  void contentTypeIsStrict(String type)throws Exception {var req=request("auth/login",loginBody());req.setContentType(type);denied(req,400);verifyNoInteractions(authority);}
  @Test void invalidUtf8DuplicateKeysAndTrailingJsonAreRejected()throws Exception {
    for(byte[] input:new byte[][]{new byte[]{(byte)0xc3,(byte)0x28},"{\"schemaVersion\":1,\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8),"{} {}".getBytes(StandardCharsets.UTF_8),"{\"x\":\"\\ud800\"}".getBytes(StandardCharsets.UTF_8)}){var req=request("auth/login",loginBody());req.setContent(input);denied(req,400);}verifyNoInteractions(authority);
  }
  @Test void bodyLimitAppliesWithoutTrustingContentLength()throws Exception {var req=request("auth/login",loginBody());req.setContent(new byte[4097]);denied(req,413);verifyNoInteractions(authority);}
  @Test void methodQueryAndServiceCallerAreDenied()throws Exception {
    var get=request("auth/login",loginBody());get.setMethod("GET");denied(get,405);var query=request("session",null);query.setQueryString("owner=a&owner=b");denied(query,400);
    var response=new MockHttpServletResponse();handler.handle(request("session",null),response,"content","127.0.0.1");assertThat(response.getStatus()).isEqualTo(403);verifyNoInteractions(authority);
  }
  @Test void missingDbIsUnavailableNotAnonymous()throws Exception {when(authority.bootstrap(COOKIE)).thenThrow(new SQLException("secret-sentinel"));denied(request("session",null),503);}
  /** API-I08 opt-in adapter chỉ issue proof; không auth cookie hay reset commit.
   * Double chứng minh HTTP dispatch, không actual KDF/DB/TLS/browser. */
  @Test void explicitReauthPassesPasswordOnlyToIdentityAndNeverIssuesCookie()throws Exception {
    var reauth=mock(ReauthAuthority.class);handler=new SessionHttpHandler(authority,ORIGIN,reauth);
    // Intent sentinel khác cookie/CSRF; không để assertion redaction nhầm
    // substring hợp lệ của digest với việc adapter làm lộ credential.
    var body=Map.<String,Object>of("schemaVersion",1,"expectedContextId",CONTEXT,"password","valid password value","purpose","LEARNING_RESET","intentHash","e".repeat(64));
    when(reauth.issueLearningReset(COOKIE,CONTEXT,CSRF,"valid password value","127.0.0.1",KEY,"e".repeat(64))).thenReturn(new ReauthAuthority.IssuedProof("c".repeat(43),OP,Instant.parse("2026-10-09T00:05:00.123999Z")));
    var response=call(request("auth/reauth",body));assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    var json=WireCodec.strict(response.getContentAsByteArray());assertThat(json.get("state").asString()).isEqualTo("ISSUED");assertThat(json.get("proof").asString()).isEqualTo("c".repeat(43));assertThat(json.get("expiresAt").asString()).isEqualTo("2026-10-09T00:05:00.123Z");assertThat(response.getContentAsString()).doesNotContain("password",COOKIE,CSRF,"userId","COMPLETED");
    verify(reauth).issueLearningReset(COOKIE,CONTEXT,CSRF,"valid password value","127.0.0.1",KEY,"e".repeat(64));verifyNoInteractions(authority);
  }
  @Test void oldRuntimeCannotEnableReauthOrPasswordOnlyFallback()throws Exception {
    denied(request("auth/reauth",Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"password","valid password value","purpose","LEARNING_RESET","intentHash","a".repeat(64))),503);verifyNoInteractions(authority);
  }
  @ParameterizedTest @ValueSource(strings={"purpose","intentHash","owner","key","origin","csrf","context","method"})
  void reauthBindingAndBoundaryAreRequiredBeforeKdf(String fault)throws Exception {
    var reauth=mock(ReauthAuthority.class);handler=new SessionHttpHandler(authority,ORIGIN,reauth);
    var body=new java.util.HashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"password","valid password value","purpose","LEARNING_RESET","intentHash","a".repeat(64)));
    switch(fault){case "purpose"->body.put("purpose","LOGOUT_ALL");case "intentHash"->body.put("intentHash","A".repeat(64));case "owner"->body.put("userId",USER);case "context"->body.put("expectedContextId","bad-uuid");default->{}}
    var r=request("auth/reauth",body);int status=400;
    if(fault.equals("key"))r.removeHeader("Idempotency-Key");
    if(fault.equals("origin")){r.removeHeader("Origin");status=403;}
    if(fault.equals("csrf")){r.addHeader("X-CSRF-TOKEN","extra");}
    if(fault.equals("method")){r.setMethod("GET");status=405;}
    denied(r,status);verifyNoInteractions(authority,reauth);
  }
  @Test void reauthDependencyAndCanonicalCsrfDenyAreRedacted()throws Exception {
    var reauth=mock(ReauthAuthority.class);handler=new SessionHttpHandler(authority,ORIGIN,reauth);
    var body=Map.<String,Object>of("schemaVersion",1,"expectedContextId",CONTEXT,"password","secret-sentinel","purpose","LEARNING_RESET","intentHash","a".repeat(64));
    when(reauth.issueLearningReset(any(),any(),any(),any(),any(),any(),any())).thenThrow(new WireCodec.Denied(403,"CSRF_DENIED"));denied(request("auth/reauth",body),403);
    // doThrow tránh gọi lại stub đang throw CSRF_DENIED lúc thay fixture.
    doThrow(new SQLException("secret-sentinel")).when(reauth).issueLearningReset(any(),any(),any(),any(),any(),any(),any());denied(request("auth/reauth",body),503);
  }
  /** Canonical adapter báo actual completed từ initiating status, không từ
   * gửi message hoặc timeout. Double chỉ kiểm HTTP state, không dual ACK proof. */
  @Test void canonicalLogoutRetryReportsActualCompletedWithoutCookie()throws Exception {
    handler=new SessionHttpHandler(authority,ORIGIN,mock(ReauthAuthority.class),18081);
    when(authority.logout(COOKIE,CONTEXT,CSRF,KEY)).thenReturn(OP);when(authority.operationStatus(COOKIE,CONTEXT,OP)).thenReturn("COMPLETED");
    var r=request("auth/logout",Map.of("schemaVersion",1,"expectedContextId",CONTEXT));r.setLocalPort(18081);
    var out=call(r);assertThat(out.getStatus()).isEqualTo(200);assertThat(WireCodec.strict(out.getContentAsByteArray()).get("state").asString()).isEqualTo("COMPLETED");assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    // Mỗi HTTP request có body stream riêng; reuse mock đã đọc hết stream
    // sẽ chỉ chứng minh BAD_WIRE, không kiểm được nhánh pending.
    when(authority.operationStatus(COOKIE,CONTEXT,OP)).thenReturn("PENDING_FENCES");
    var pending=request("auth/logout",Map.of("schemaVersion",1,"expectedContextId",CONTEXT));pending.setLocalPort(18081);assertThat(call(pending).getStatus()).isEqualTo(202);
  }
  @Test void canonicalPublicHandlerCannotAcceptLegacyOrArbitraryPort()throws Exception{
    handler=new SessionHttpHandler(authority,ORIGIN,mock(ReauthAuthority.class),18081);
    denied(request("session",null),403);verifyNoInteractions(authority);
    assertThatThrownBy(()->new SessionHttpHandler(authority,ORIGIN,null,12345)).isInstanceOf(IllegalArgumentException.class);
  }

  /** I08 chỉ chuyển password tới own sensitive core cùng exact purpose/key/hash;
   * adapter không tự cấp owner, reserve Learning hoặc phát auth cookie. */
  @ParameterizedTest @ValueSource(strings={"LOGOUT_ALL","PASSWORD_CHANGE"})
  void canonicalSensitiveReauthUsesBoundPurposeWithoutCookie(String purpose)throws Exception {
    var proofOwner=mock(ReauthAuthority.class);var sensitive=mock(IdentitySensitiveAuthority.class);
    handler=new SessionHttpHandler(authority,ORIGIN,proofOwner,18081,sensitive);
    String intent="e".repeat(64),password="valid current password value";
    when(sensitive.issue(COOKIE,CONTEXT,CSRF,password,"127.0.0.1",KEY,purpose,intent))
      .thenReturn(new ReauthAuthority.IssuedProof("c".repeat(43),OP,Instant.now().plusSeconds(300)));
    var req=request("auth/reauth",Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"password",password,"purpose",purpose,"intentHash",intent));req.setLocalPort(18081);
    var out=call(req);assertThat(out.getStatus()).isEqualTo(200);assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    var json=WireCodec.strict(out.getContentAsByteArray());assertThat(json.get("purpose").asString()).isEqualTo(purpose);
    assertThat(json.get("operationKey").asString()).isEqualTo(KEY.toString());assertThat(json.get("intentHash").asString()).isEqualTo(intent);
    assertThat(out.getContentAsString()).doesNotContain(password,COOKIE,CSRF,"userId","COMPLETED");
    verify(sensitive).issue(COOKIE,CONTEXT,CSRF,password,"127.0.0.1",KEY,purpose,intent);
    verifyNoInteractions(authority,proofOwner);
  }

  /** Consumed retry không yêu cầu bootstrap ACTIVE; chỉ core bound status được
   * dùng. Không kiểm password-only, rotate cookie hoặc gọi interactive để renew. */
  @ParameterizedTest @ValueSource(strings={"logout-all","password-change"})
  void canonicalSensitiveCommandsUseProofOnlyAndStatusWithoutBootstrap(String command)throws Exception {
    var proofOwner=mock(ReauthAuthority.class);var sensitive=mock(IdentitySensitiveAuthority.class);
    handler=new SessionHttpHandler(authority,ORIGIN,proofOwner,18081,sensitive);
    String proof="c".repeat(43),password=command.equals("password-change")?"valid new password value":null;
    String purpose=command.equals("logout-all")?"LOGOUT_ALL":"PASSWORD_CHANGE";
    var body=new java.util.HashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"reauthProof",proof));
    if(password!=null)body.put("newPassword",password);
    when(sensitive.execute(COOKIE,CONTEXT,CSRF,KEY,purpose,proof,password)).thenReturn(OP);
    when(authority.operationStatus(COOKIE,CONTEXT,OP)).thenReturn("PENDING_FENCES");
    var req=request("auth/"+command,body);req.setLocalPort(18081);var out=call(req);
    assertThat(out.getStatus()).isEqualTo(202);assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    assertThat(out.getContentAsString()).doesNotContain(proof,COOKIE,CSRF,"userId","credentialEpoch","newPassword");
    verify(sensitive).execute(COOKIE,CONTEXT,CSRF,KEY,purpose,proof,password);
    verify(authority).operationStatus(COOKIE,CONTEXT,OP);verifyNoMoreInteractions(authority);verifyNoInteractions(proofOwner);
  }

  @ParameterizedTest @ValueSource(strings={"origin","csrf","owner","proof","key","purpose","port"})
  void canonicalSensitiveBoundaryRejectsBeforeCore(String fault)throws Exception {
    var proofOwner=mock(ReauthAuthority.class);var sensitive=mock(IdentitySensitiveAuthority.class);
    handler=new SessionHttpHandler(authority,ORIGIN,proofOwner,18081,sensitive);
    var body=new java.util.HashMap<String,Object>(Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"reauthProof","c".repeat(43)));
    if(fault.equals("owner"))body.put("userId",USER);
    if(fault.equals("proof"))body.remove("reauthProof");
    if(fault.equals("purpose")){body.remove("reauthProof");body.put("purpose","UNKNOWN");body.put("password","valid current password value");body.put("intentHash","e".repeat(64));}
    var req=request(fault.equals("purpose")?"auth/reauth":"auth/logout-all",body);req.setLocalPort(18081);
    int status=400;
    if(fault.equals("origin")){req.removeHeader("Origin");status=403;}
    if(fault.equals("csrf"))req.addHeader("X-CSRF-TOKEN","extra");
    if(fault.equals("key"))req.removeHeader("Idempotency-Key");
    if(fault.equals("port")){req.setLocalPort(8080);status=403;}
    denied(req,status);verifyNoInteractions(authority,proofOwner,sensitive);
  }

  @Test void canonicalSensitiveCoreDenyAndStorageFailureAreRedacted()throws Exception {
    var sensitive=mock(IdentitySensitiveAuthority.class);
    handler=new SessionHttpHandler(authority,ORIGIN,mock(ReauthAuthority.class),18081,sensitive);
    var body=Map.<String,Object>of("schemaVersion",1,"expectedContextId",CONTEXT,"reauthProof","secret-sentinel");
    when(sensitive.execute(any(),any(),any(),any(),any(),any(),isNull())).thenThrow(new WireCodec.Denied(403,"CSRF_DENIED"));
    var first=request("auth/logout-all",body);first.setLocalPort(18081);denied(first,403);
    doThrow(new SQLException("secret-sentinel")).when(sensitive).execute(any(),any(),any(),any(),any(),any(),isNull());
    var retry=request("auth/logout-all",body);retry.setLocalPort(18081);denied(retry,503);verifyNoInteractions(authority);
  }

  @Test void canonicalSensitiveCompositionCannotEnableStandaloneOrMissingProofOwner()throws Exception {
    var sensitive=mock(IdentitySensitiveAuthority.class);var proofOwner=mock(ReauthAuthority.class);
    assertThatThrownBy(()->new SessionHttpHandler(authority,ORIGIN,proofOwner,8080,sensitive)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->new SessionHttpHandler(authority,ORIGIN,null,18081,sensitive)).isInstanceOf(IllegalArgumentException.class);
    when(authority.bootstrap(COOKIE)).thenReturn(active());
    assertThat(WireCodec.strict(call(request("session",null)).getContentAsByteArray()).get("capabilities").get("identitySensitiveReauth").asBoolean()).isFalse();
    handler=new SessionHttpHandler(authority,ORIGIN,proofOwner,18081,sensitive);var req=request("session",null);req.setLocalPort(18081);
    assertThat(WireCodec.strict(call(req).getContentAsByteArray()).get("capabilities").get("identitySensitiveReauth").asBoolean()).isTrue();
  }

  @Test void canonicalSensitiveRetryReturnsCompletedWithoutClearingCookie()throws Exception {
    var sensitive=mock(IdentitySensitiveAuthority.class);
    handler=new SessionHttpHandler(authority,ORIGIN,mock(ReauthAuthority.class),18081,sensitive);
    when(sensitive.execute(COOKIE,CONTEXT,CSRF,KEY,"LOGOUT_ALL","c".repeat(43),null)).thenReturn(OP);
    when(authority.operationStatus(COOKIE,CONTEXT,OP)).thenReturn("COMPLETED");
    var req=request("auth/logout-all",Map.of("schemaVersion",1,"expectedContextId",CONTEXT,"reauthProof","c".repeat(43)));req.setLocalPort(18081);
    var out=call(req);assertThat(out.getStatus()).isEqualTo(200);assertThat(out.getHeaders("Set-Cookie")).isEmpty();
    assertThat(WireCodec.strict(out.getContentAsByteArray()).get("state").asString()).isEqualTo("COMPLETED");
    verify(authority).operationStatus(COOKIE,CONTEXT,OP);verifyNoMoreInteractions(authority);
  }
}
