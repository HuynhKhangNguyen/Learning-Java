package lab.backend.identity.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Chặn route chưa đăng ký. Foundation không cấp user admission; canonical
 * opt-in chỉ dispatch sau verified mTLS caller và đúng listener topology. */
public final class TransportBoundaryFilter extends OncePerRequestFilter {
  private final TransportSettings settings;
  private final lab.backend.identity.session.SessionHttpHandler session;
  private final lab.backend.identity.delivery.DeliveryHttpHandler delivery;
  private final lab.backend.identity.session.CanonicalAuthHttpHandler canonical;
  public TransportBoundaryFilter(TransportSettings settings){this(settings,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.identity.session.SessionHttpHandler session){this(settings,session,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.identity.session.SessionHttpHandler session,lab.backend.identity.delivery.DeliveryHttpHandler delivery){this(settings,session,delivery,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.identity.session.SessionHttpHandler session,lab.backend.identity.delivery.DeliveryHttpHandler delivery,lab.backend.identity.session.CanonicalAuthHttpHandler canonical){this.settings=settings;this.session=session;this.delivery=delivery;this.canonical=canonical;}
  private static final String PUBLIC="/api/v1/transport/status",PRIVATE="/internal/v1/transport/probes";
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse response,FilterChain unused)throws ServletException,IOException{
    response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setContentType("application/json");
    try{
      if(!req.isSecure() || !(req.getAttribute("jakarta.servlet.request.X509Certificate") instanceof X509Certificate[] chain) || chain.length==0)throw new WireCodec.Denied(403,"SERVICE_AUTH_REQUIRED");
      // Certificate caller vẫn quyết boundary; named port không tự cấp quyền.
      String caller=CertificateIdentity.identity(chain[0],"lab",true);int port=req.getLocalPort();boolean publicListener=port==settings.publicPort();
      if((publicListener && !caller.equals("edge")) || (!publicListener && (port!=settings.privatePort() || caller.equals("edge"))))throw new WireCodec.Denied(403,"CALLER_DENIED");
      for(String h:new String[]{"X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto"})if(req.getHeader(h)!=null)throw new WireCodec.Denied(403,"UNTRUSTED_HEADER");
      String path=req.getRequestURI();if(path.contains("%") || path.contains(";") || path.contains("//") || path.contains("\\") || req.getQueryString()!=null)throw new WireCodec.Denied(400,"AMBIGUOUS_ROUTE");
      // Only this opt-in public path can receive browser Origin/Cookie. The
      // trusted edge must overwrite the dedicated IP header, never forward a
      // browser-supplied value. Certificate identity is verified first above.
      if(publicListener && session!=null && session.handles(path)){
        settings.dbTime();
        var ips=req.getHeaders("X-Backend-Lab-Client-IP");
        if(ips==null || !ips.hasMoreElements())throw new WireCodec.Denied(403,"TRUSTED_SOURCE_REQUIRED");
        String ip=ips.nextElement();
        if(ips.hasMoreElements() || ip.length()>45 || !(ip.matches("[0-9.]+") || ip.matches("[0-9a-fA-F:]+")))throw new WireCodec.Denied(403,"TRUSTED_SOURCE_REQUIRED");
        if(!ip.contains(":")){
          String[] parts=ip.split("\\.",-1);
          if(parts.length!=4)throw new WireCodec.Denied(403,"TRUSTED_SOURCE_REQUIRED");
          for(String part:parts)if(!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part)>255)throw new WireCodec.Denied(403,"TRUSTED_SOURCE_REQUIRED");
        }
        try{ip=java.net.InetAddress.getByName(ip).getHostAddress();}catch(java.net.UnknownHostException invalid){throw new WireCodec.Denied(403,"TRUSTED_SOURCE_REQUIRED");}
        session.handle(req,response,caller,ip);return;
      }
      if(req.getHeader("Origin")!=null || req.getHeader("X-Backend-Lab-Client-IP")!=null || (!publicListener && req.getHeader("Cookie")!=null))throw new WireCodec.Denied(403,"SERVICE_AUTH_REQUIRED");
      if(publicListener){
        if(!path.equals(PUBLIC))throw new WireCodec.Denied(404,"ROUTE_NOT_AVAILABLE");if(!req.getMethod().equals("GET"))throw new WireCodec.Denied(405,"METHOD_DENIED");
        settings.dbTime();respond(response,200,Map.of("schemaVersion",1,"service",TransportSettings.SERVICE,"phase","transport-foundation","businessReady",false,"clockVerified",true));return;
      }
      if(canonical!=null&&canonical.handles(path)){
        // HTTPS/certificate/listener/spoof/browser headers đã kiểm trước body.
        // Canonical handler kiểm lại route/method/schema, authority kiểm own DB.
        settings.dbTime();canonical.handle(req,response,caller);return;
      }
      if(delivery!=null){settings.dbTime();if(delivery.handle(req,response,caller))return;}
      if(!path.equals(PRIVATE))throw new WireCodec.Denied(404,"ROUTE_NOT_AVAILABLE");if(!req.getMethod().equals("POST"))throw new WireCodec.Denied(405,"METHOD_DENIED");
      String type=req.getContentType();if(type==null || !(type.equalsIgnoreCase("application/json") || type.equalsIgnoreCase("application/json;charset=utf-8") || type.equalsIgnoreCase("application/json; charset=utf-8")))throw new WireCodec.Denied(400,"BAD_CONTENT_TYPE");
      WireCodec.Probe probe=WireCodec.probe(req.getInputStream().readNBytes(WireCodec.MAX_BYTES+1));
      if(!probe.audience().equals(TransportSettings.SERVICE) || !allowed(caller,probe.action()))throw new WireCodec.Denied(403,"ACTION_DENIED");
      WireCodec.window(probe,settings.dbTime());
      respond(response,200,Map.of("schemaVersion",1,"service",TransportSettings.SERVICE,"phase","transport-probe","businessReady",false,"caller",caller,"expectedRevision",probe.revision(),"requestBinding",probe.binding()));
    }catch(java.net.SocketTimeoutException timeout){respond(response,408,WireCodec.error("REQUEST_TIMEOUT"));}
    catch(WireCodec.Denied denied){respond(response,denied.status,WireCodec.error(denied.code));}catch(IllegalStateException denied){respond(response,503,WireCodec.error("CLOCK_UNAVAILABLE"));}
  }
  public static boolean allowed(String caller,String action){
    if(action.equals("OPS_TRANSPORT"))return caller.equals("operator");
    return switch(TransportSettings.SERVICE){
      case "identity"->action.equals("ADMISSION_TRANSPORT") && Set.of("content","learning").contains(caller);
      case "content"->(action.equals("FENCE_TRANSPORT") && caller.equals("identity")) || (action.equals("BUNDLE_TRANSPORT") && caller.equals("learning"));
      case "learning"->(action.equals("FENCE_TRANSPORT") && caller.equals("identity")) || (action.equals("PUBLICATION_TRANSPORT") && caller.equals("content"));
      default->false;
    };
  }
  private static void respond(HttpServletResponse response,int status,Object body)throws IOException{response.setStatus(status);response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(body));}
}
