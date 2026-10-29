package lab.backend.identity.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lab.backend.identity.credential.CredentialWorkflow.Purpose;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;
import tools.jackson.databind.JsonNode;

/** Public adapter chỉ đăng ký bằng owned lab profile explicit. Boundary filter
 * kiểm public-listener mTLS edge và trusted source IP trước handler; body/context
 * không tự cấp quyền. Canonical authority quyết proof/effect dưới own DB locks. */
public final class SessionHttpHandler {
  public static final int MAX_BYTES=4096;
  private static final String SESSION="/api/v1/session",AUTH="/api/v1/auth/";
  private static final Set<String> COMMANDS=Set.of("login","logout","activate","password-reset","logout-all","password-change","reauth");
  private final SessionAuthority authority;
  private final ReauthAuthority reauth;
  private final IdentitySensitiveAuthority sensitive;
  private final String origin;
  private final int publicPort;
  private final boolean globalCompletion;
  public SessionHttpHandler(SessionAuthority authority,String origin) {
    this(authority,origin,null);
  }
  /** R04a constructor giữ capability unavailable; chỉ composition canonical
   * explicit mới truyền proof authority. Không tự bật từ việc class tồn tại. */
  public SessionHttpHandler(SessionAuthority authority,String origin,ReauthAuthority reauth) {
    this(authority,origin,reauth,8080);
  }
  /** Port chỉ từ hai topology có tên; canonical composition phải verify own
   * coordinator trước tạo adapter. Không nhận arbitrary listener override. */
  public SessionHttpHandler(SessionAuthority authority,String origin,ReauthAuthority reauth,int publicPort) {
    this(authority,origin,reauth,publicPort,null);
  }
  /** Sensitive flow chỉ cho named canonical topology sau startup verifyV7;
   * constructor cũ không tự nhận capability mới hoặc password-only fallback. */
  public SessionHttpHandler(SessionAuthority authority,String origin,ReauthAuthority reauth,int publicPort,
      IdentitySensitiveAuthority sensitive) {
    if(publicPort!=8080&&publicPort!=18081)throw new IllegalArgumentException("Named Identity public port required");
    if(sensitive!=null&&(publicPort!=18081||reauth==null))throw new IllegalArgumentException("Sensitive reauth requires canonical composition");
    var uri=java.net.URI.create(origin);
    if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !uri.getRawPath().isEmpty() || !uri.toString().equals(origin))throw new IllegalArgumentException("Exact HTTPS origin required");
    this.authority=authority;this.origin=origin;this.reauth=reauth;this.sensitive=sensitive;this.publicPort=publicPort;this.globalCompletion=publicPort==18081&&reauth!=null;
  }
  public boolean handles(String path){return SESSION.equals(path) || (path.startsWith(AUTH) && (COMMANDS.contains(path.substring(AUTH.length())) || path.startsWith(AUTH+"operations/")));}

  public void handle(HttpServletRequest request,HttpServletResponse response,String verifiedCaller,String trustedIp)throws IOException {
    response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
    response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
    try {
      if(!request.isSecure() || request.getLocalPort()!=publicPort || !"edge".equals(verifiedCaller))throw new Denied(403,"CALLER_DENIED");
      for(String header:new String[]{"X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto"})if(request.getHeader(header)!=null)throw new Denied(403,"UNTRUSTED_HEADER");
      String path=request.getRequestURI();
      if(path.contains("%") || path.contains(";") || path.contains("//") || path.contains("\\") || request.getQueryString()!=null)throw new Denied(400,"AMBIGUOUS_ROUTE");
      if(!handles(path))throw new Denied(404,"ROUTE_NOT_AVAILABLE");
      for(String header:new String[]{"X-CSRF-TOKEN","Idempotency-Key","X-Session-Context"})single(request,header);
      String suppliedOrigin=single(request,"Origin"),flow=single(request,"X-Auth-Flow");
      boolean credentialFlow="credential".equals(flow);
      if(flow!=null && (!credentialFlow || !path.equals(SESSION)))throw new Denied(400,"BAD_WIRE");
      boolean write=!path.equals(SESSION) && !path.startsWith(AUTH+"operations/");
      if((write || credentialFlow || suppliedOrigin!=null) && !origin.equals(suppliedOrigin))throw new Denied(403,"ORIGIN_DENIED");
      if(!((write || credentialFlow)?"POST":"GET").equals(request.getMethod()))throw new Denied(405,"METHOD_DENIED");
      String cookie=cookie(request);
      if(credentialFlow) {
        // Initial mint cannot require a token that does not exist yet. Exact
        // Origin + non-simple JSON/custom header protects this explicit flow;
        // POST causes a browser to supply Origin without forbidden-header hacks.
        JsonNode body=body(request);Set<String> names=new HashSet<>();body.properties().forEach(p->names.add(p.getKey()));
        if(!names.equals(Set.of("schemaVersion")) || !body.get("schemaVersion").isInt() || body.get("schemaVersion").asInt()!=1)throw new Denied(400,"UNSUPPORTED_SCHEMA");
        var issued=authority.begin(cookie);emit(response,issued);respond(response,200,view(issued.view()));return;
      }
      if(!write) {
        if(request.getInputStream().read()!=-1)throw new Denied(400,"BAD_WIRE");
        if(path.equals(SESSION)) {
          respond(response,200,view(authority.bootstrap(cookie)));
        } else {
          UUID operation=uuid(path.substring((AUTH+"operations/").length()));
          UUID context=uuid(single(request,"X-Session-Context"));
          respond(response,200,operation(operation,authority.operationStatus(cookie,context,operation)));
        }
        return;
      }
      JsonNode body=body(request);String command=path.substring(AUTH.length());validate(body,command);
      UUID context=uuid(WireCodec.string(body,"expectedContextId"));String csrf=single(request,"X-CSRF-TOKEN");
      switch(command) {
        case "login" -> {var issued=authority.login(cookie,context,csrf,text(body,"loginName",256),text(body,"password",512),trustedIp);emit(response,issued);respond(response,200,view(issued.view()));}
        case "logout" -> {
          UUID op=authority.logout(cookie,context,csrf,uuid(single(request,"Idempotency-Key")));
          // Canonical retry trả actual state sau commit, không báo hoàn tất từ
          // timeout hoặc chỉ một ACK. Status lookup vẫn initiating binding.
          String state=globalCompletion?authority.operationStatus(cookie,context,op):"PENDING_FENCES";
          respond(response,state.equals("COMPLETED")?200:202,operation(op,state));
        }
        case "activate","password-reset" -> {
          var result=authority.consume(command.equals("activate")?Purpose.ACTIVATE:Purpose.RESET,cookie,context,csrf,text(body,"token",128),text(body,"newPassword",512),trustedIp,
              command.equals("password-reset")?uuid(single(request,"Idempotency-Key")):null);
          respond(response,result.operationId()==null?200:202,result.operationId()==null?Map.of("schemaVersion",1,"state",result.state()):operation(result.operationId(),result.state()));
        }
        case "reauth" -> {
          // Password chỉ tới Identity; user/session/epoch là canonical callback,
          // không nhận owner từ body. Proof không login/rotate cookie/reset data.
          String purpose=text(body,"purpose",64);
          if(!Set.of(ReauthAuthority.PURPOSE,"LOGOUT_ALL","PASSWORD_CHANGE").contains(purpose)
              ||(!ReauthAuthority.PURPOSE.equals(purpose)&&sensitive==null))throw new Denied(400,"BAD_REAUTH_BINDING");
          String intent=text(body,"intentHash",64);if(!intent.matches("[0-9a-f]{64}"))throw new Denied(400,"BAD_REAUTH_BINDING");
          UUID key=uuid(single(request,"Idempotency-Key"));
          if(reauth==null)throw new Denied(503,"REAUTH_CAPABILITY_UNAVAILABLE");
          var proof=ReauthAuthority.PURPOSE.equals(purpose)
            ?reauth.issueLearningReset(cookie,context,csrf,text(body,"password",512),trustedIp,key,intent)
            :sensitive.issue(cookie,context,csrf,text(body,"password",512),trustedIp,key,purpose,intent);
          var format=new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
          respond(response,200,Map.of("schemaVersion",1,"state","ISSUED","proof",proof.proof(),"grantRef",proof.grantRef().toString(),"expiresAt",format.format(proof.expiresAt()),"purpose",purpose,"operationKey",key.toString(),"intentHash",intent));
        }
        case "logout-all","password-change" -> {
          if(sensitive==null)unavailableSensitive(cookie,context,csrf);
          String purpose=command.equals("logout-all")?"LOGOUT_ALL":"PASSWORD_CHANGE";
          UUID op=sensitive.execute(cookie,context,csrf,uuid(single(request,"Idempotency-Key")),purpose,
            text(body,"reauthProof",128),command.equals("password-change")?text(body,"newPassword",512):null);
          // Không bootstrap ACTIVE trước consumed retry: chỉ initiating binding
          // trong authority được đọc operation cũ sau deny. Không cookie/idle touch.
          String state=authority.operationStatus(cookie,context,op);
          respond(response,state.equals("COMPLETED")?200:202,operation(op,state));
        }
        default -> throw new Denied(400,"BAD_WIRE");
      }
    }catch(Denied denied){respond(response,denied.status,error(denied.code));}
    catch(java.net.SocketTimeoutException timeout){respond(response,408,error("REQUEST_TIMEOUT"));}
    catch(SQLException|RuntimeException unavailable){respond(response,503,error("DEPENDENCY_UNAVAILABLE"));}
  }
  /** Standalone R04a giữ binding precheck nhưng không cấp password fallback;
   * bootstrap read-only, không biến unsupported command thành interactive. */
  private void unavailableSensitive(String cookie,UUID context,String csrf)throws SQLException {
    var current=authority.bootstrap(cookie);if(!"ACTIVE".equals(current.state()))throw new Denied(401,"SESSION_INVALID");
    if(!context.equals(current.contextId()))throw new Denied(409,"SESSION_CONTEXT_MISMATCH");
    byte[] expected=SessionAuthority.digest(current.csrfToken()),actual=SessionAuthority.digest(csrf);
    if(actual==null||expected==null||!MessageDigest.isEqual(expected,actual))throw new Denied(403,"CSRF_DENIED");
    throw new Denied(503,"REAUTH_CAPABILITY_UNAVAILABLE");
  }
  private static JsonNode body(HttpServletRequest request)throws IOException {
    String type=request.getContentType();
    if(type==null || !(type.equalsIgnoreCase("application/json") || type.equalsIgnoreCase("application/json;charset=utf-8") || type.equalsIgnoreCase("application/json; charset=utf-8")))throw new Denied(400,"BAD_CONTENT_TYPE");
    if(request.getContentLengthLong()>MAX_BYTES)throw new Denied(413,"PAYLOAD_TOO_LARGE");
    byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new Denied(413,"PAYLOAD_TOO_LARGE");return WireCodec.strict(bytes);
  }
  private static void validate(JsonNode n,String command) {
    Set<String> fields=new HashSet<>(Set.of("schemaVersion","expectedContextId"));
    switch(command){case "login"->fields.addAll(Set.of("loginName","password"));case "activate","password-reset"->fields.addAll(Set.of("token","newPassword"));case "logout-all"->fields.add("reauthProof");case "password-change"->fields.addAll(Set.of("reauthProof","newPassword"));case "reauth"->fields.addAll(Set.of("password","purpose","intentHash"));default->{}}
    Set<String> actual=new HashSet<>();n.properties().forEach(p->actual.add(p.getKey()));
    if(!actual.equals(fields) || !n.get("schemaVersion").isInt() || n.get("schemaVersion").asInt()!=1)throw new Denied(400,"UNSUPPORTED_SCHEMA");
    for(String field:fields)if(!field.equals("schemaVersion"))text(n,field,field.equals("newPassword")?512:field.equals("password")?512:256);
  }
  private static String text(JsonNode n,String field,int max){String value=WireCodec.string(n,field);if(value.isEmpty() || value.length()>max)throw new Denied(400,"BAD_WIRE");return value;}
  private static UUID uuid(String value){WireCodec.idempotencyKey(value);return UUID.fromString(value);}
  private static String single(HttpServletRequest request,String name){var values=request.getHeaders(name);if(values==null || !values.hasMoreElements())return null;String value=values.nextElement();if(values.hasMoreElements())throw new Denied(400,"AMBIGUOUS_HEADER");return value;}
  private static String cookie(HttpServletRequest request) {
    var lines=request.getHeaders("Cookie");String value=null;
    while(lines!=null && lines.hasMoreElements()) {
      String line=lines.nextElement();if(line.length()>8192)throw new Denied(400,"BAD_COOKIE");
      for(String pair:line.split(";",-1)) {
        String p=pair.trim();int separator=p.indexOf('=');
        if(separator>0 && p.substring(0,separator).equals(SessionAuthority.COOKIE)) {
          if(value!=null)throw new Denied(400,"AMBIGUOUS_COOKIE");value=p.substring(separator+1);
          if(SessionAuthority.digest(value)==null)throw new Denied(400,"BAD_COOKIE");
        }
      }
    }
    return value;
  }
  private Map<String,Object> view(SessionAuthority.View v) {
    Map<String,Object> out=new HashMap<>();out.put("schemaVersion",1);out.put("state",v.state());
    out.put("capabilities",Map.of("reauth",reauth!=null,"identitySensitiveReauth",sensitive!=null,"globalRevocationCompletion",globalCompletion));
    if(v.contextId()!=null)out.put("sessionContextId",v.contextId());if(v.csrfToken()!=null)out.put("csrfToken",v.csrfToken());
    if(v.userId()!=null)out.put("userId",v.userId());if(v.credentialEpoch()!=null)out.put("credentialEpoch",v.credentialEpoch());
    if(v.operationId()!=null){out.put("operationId",v.operationId());out.put("statusPath",AUTH+"operations/"+v.operationId());}return out;
  }
  private static Map<String,Object> operation(UUID op,String state){return Map.of("schemaVersion",1,"state",state,"operationId",op,"statusPath",AUTH+"operations/"+op);}
  private static Map<String,Object> error(String code){return Map.of("schemaVersion",1,"code",code,"traceId",UUID.randomUUID(),"messageKey","identity.auth."+code.toLowerCase(java.util.Locale.ROOT));}
  private static void emit(HttpServletResponse response,SessionAuthority.Issued issued) {
    if(issued.cookie()==null)return;
    long remaining=Math.max(0,Math.min(issued.maxAge(),Duration.between(Instant.now(),issued.expiresAt()).getSeconds()));
    if(remaining==0)throw new Denied(503,"AUTH_ISSUANCE_EXPIRED");
    response.addHeader("Set-Cookie",SessionAuthority.COOKIE+"="+issued.cookie()+"; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age="+remaining+"; Expires="+DateTimeFormatter.RFC_1123_DATE_TIME.format(issued.expiresAt().atZone(ZoneOffset.UTC)));
  }
  private static void respond(HttpServletResponse response,int status,Object body)throws IOException {response.setStatus(status);response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(body));}
}
