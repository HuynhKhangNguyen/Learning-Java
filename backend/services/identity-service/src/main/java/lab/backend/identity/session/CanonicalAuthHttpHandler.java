package lab.backend.identity.session;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import lab.backend.identity.transport.WireCodec;
import lab.backend.identity.transport.WireCodec.Denied;

/** Private INT-01/06 adapter. Boundary filter phải xác minh certificate mTLS
 * trước truyền caller; header/browser không được tự chọn identity. Chưa đăng ký
 * ở profile session R04a: canonical composition sẽ opt-in riêng. */
public final class CanonicalAuthHttpHandler {
  private final SessionAuthority sessions;
  private final ReauthAuthority reauth;
  private final String origin;
  private final int privatePort;
  public CanonicalAuthHttpHandler(SessionAuthority sessions,ReauthAuthority reauth,String origin) {
    this(sessions,reauth,origin,8443);
  }
  /** Named three-app lab dùng18441; profile cũ giữ8443, không arbitrary port. */
  public CanonicalAuthHttpHandler(SessionAuthority sessions,ReauthAuthority reauth,String origin,int privatePort) {
    if(privatePort!=8443&&privatePort!=18441)throw new IllegalArgumentException("Named Identity private port required");
    this.sessions=Objects.requireNonNull(sessions);this.reauth=Objects.requireNonNull(reauth);
    // Dùng cùng exact-Origin policy của public adapter, không nhận Origin từ
    // private HTTP header làm authority cho learner request đã được bind body.
    new SessionHttpHandler(sessions,origin);this.origin=origin;this.privatePort=privatePort;
  }
  public boolean handles(String path){return CanonicalAdmissionWire.PATH.equals(path)||ReauthReservationWire.PATH.equals(path);}
  public void handle(HttpServletRequest request,HttpServletResponse response,String verifiedCaller)throws IOException {
    response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
    response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
    try {
      if(!request.isSecure()||request.getLocalPort()!=privatePort)throw new Denied(403,"PRIVATE_LISTENER_REQUIRED");
      String path=request.getRequestURI();
      if(path==null||path.contains("%")||path.contains(";")||path.contains("//")||path.contains("\\")||request.getQueryString()!=null)throw new Denied(400,"AMBIGUOUS_ROUTE");
      if(!handles(path))throw new Denied(404,"ROUTE_NOT_AVAILABLE");
      boolean reservation=ReauthReservationWire.PATH.equals(path);
      if(reservation?!"learning".equals(verifiedCaller):!Set.of("content","learning").contains(verifiedCaller==null?"":verifiedCaller))throw new Denied(403,"CALLER_DENIED");
      for(String name:new String[]{"Origin","Cookie","X-Backend-Lab-Client-IP","X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto","X-CSRF-TOKEN","X-Session-Context","X-Auth-Flow","Idempotency-Key"})
        if(request.getHeader(name)!=null)throw new Denied(403,"UNTRUSTED_HEADER");
      if(!"POST".equals(request.getMethod()))throw new Denied(405,"METHOD_DENIED");
      var values=request.getHeaders("Content-Type");if(values!=null&&values.hasMoreElements()){values.nextElement();if(values.hasMoreElements())throw new Denied(400,"AMBIGUOUS_HEADER");}
      String type=request.getContentType();if(type==null||!type.matches("(?i)application/json(?:; ?charset=utf-8)?"))throw new Denied(400,"BAD_CONTENT_TYPE");
      int max=reservation?ReauthReservationWire.MAX_BYTES:CanonicalAdmissionWire.MAX_BYTES;
      if(request.getContentLengthLong()>max)throw new Denied(413,"PAYLOAD_TOO_LARGE");
      byte[] bytes=request.getInputStream().readNBytes(max+1);if(bytes.length>max)throw new Denied(413,"PAYLOAD_TOO_LARGE");
      // Exact body được authority parse lại cho INT-01; INT-06 so bindings với
      // stored grant dưới account→session→grant locks. Không cache ALLOW/proof.
      Object out=reservation?ReauthReservationWire.json(reauth.reserve(ReauthReservationWire.parse(bytes,verifiedCaller),verifiedCaller))
          :sessions.admit(bytes,verifiedCaller,origin).json();
      respond(response,200,out);
    }catch(Denied denied){respond(response,denied.status,WireCodec.error(denied.code));}
    catch(java.net.SocketTimeoutException timeout){respond(response,408,WireCodec.error("REQUEST_TIMEOUT"));}
    catch(SQLException|RuntimeException unavailable){respond(response,503,WireCodec.error("DEPENDENCY_UNAVAILABLE"));}
  }
  private static void respond(HttpServletResponse response,int status,Object body)throws IOException{response.setStatus(status);response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(body));}
}
