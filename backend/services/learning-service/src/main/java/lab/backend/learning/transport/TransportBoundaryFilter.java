package lab.backend.learning.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Deny all unregistered paths; this foundation never issues user admission. */
public final class TransportBoundaryFilter extends OncePerRequestFilter {
  private final TransportSettings settings;
  private final lab.backend.learning.identityaccess.AdmissionGate gate;
  private final lab.backend.learning.delivery.DeliveryHttpHandler delivery;
  private final lab.backend.learning.contentreplica.PublicationHttpHandler publication;
  public TransportBoundaryFilter(TransportSettings settings){this(settings,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.learning.identityaccess.AdmissionGate gate){this(settings,gate,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.learning.identityaccess.AdmissionGate gate,lab.backend.learning.delivery.DeliveryHttpHandler delivery){this(settings,gate,delivery,null);}
  public TransportBoundaryFilter(TransportSettings settings,lab.backend.learning.identityaccess.AdmissionGate gate,lab.backend.learning.delivery.DeliveryHttpHandler delivery,lab.backend.learning.contentreplica.PublicationHttpHandler publication){this.settings=settings;this.gate=gate;this.delivery=delivery;this.publication=publication;}
  private static final String PUBLIC="/api/v1/transport/status",PRIVATE="/internal/v1/transport/probes";
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse response,FilterChain unused)throws ServletException,IOException{
    response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setContentType("application/json");
    try{
      if(!req.isSecure() || !(req.getAttribute("jakarta.servlet.request.X509Certificate") instanceof X509Certificate[] chain) || chain.length==0)throw new WireCodec.Denied(403,"SERVICE_AUTH_REQUIRED");
      // Certificate caller vẫn quyết boundary; named port không tự cấp quyền.
      String caller=CertificateIdentity.identity(chain[0],"lab",true);int port=req.getLocalPort();boolean publicListener=port==settings.publicPort();
      if((publicListener && !caller.equals("edge")) || (!publicListener && (port!=settings.privatePort() || caller.equals("edge"))))throw new WireCodec.Denied(403,"CALLER_DENIED");
      for(String h:new String[]{"X-User-Id","X-Owner-Id","X-Caller","X-Audience","X-Authz-Context","X-Forwarded-For","X-Forwarded-Proto"})if(req.getHeader(h)!=null)throw new WireCodec.Denied(403,"UNTRUSTED_HEADER");
      if(req.getHeader("Origin")!=null || (!publicListener && req.getHeader("Cookie")!=null))throw new WireCodec.Denied(403,"SERVICE_AUTH_REQUIRED");
      String path=req.getRequestURI();if(path.contains("%") || path.contains(";") || path.contains("//") || path.contains("\\") || req.getQueryString()!=null)throw new WireCodec.Denied(400,"AMBIGUOUS_ROUTE");
      if(publicListener){
        if(!path.equals(PUBLIC))throw new WireCodec.Denied(404,"ROUTE_NOT_AVAILABLE");if(!req.getMethod().equals("GET"))throw new WireCodec.Denied(405,"METHOD_DENIED");
        settings.dbTime();respond(response,200,Map.of("schemaVersion",1,"service",TransportSettings.SERVICE,"phase","transport-foundation","businessReady",false,"clockVerified",true));return;
      }
      if(delivery!=null){settings.dbTime();if(delivery.handle(req,response,caller))return;}
      if(publication!=null){settings.dbTime();if(publication.handle(req,response,caller))return;}
      if(gate!=null && path.startsWith(lab.backend.learning.identityaccess.AdmissionWire.FENCE_PATH)){
        if(!caller.equals("identity"))throw new WireCodec.Denied(403,"ACTION_DENIED");
        if(!req.getMethod().equals("PUT"))throw new WireCodec.Denied(405,"METHOD_DENIED");
        String type=req.getContentType();if(type==null || !(type.equalsIgnoreCase("application/json")||type.equalsIgnoreCase("application/json;charset=utf-8")||type.equalsIgnoreCase("application/json; charset=utf-8")))throw new WireCodec.Denied(400,"BAD_CONTENT_TYPE");
        var op=java.util.UUID.fromString(WireCodec.idempotencyKey(path.substring(lab.backend.learning.identityaccess.AdmissionWire.FENCE_PATH.length())));
        settings.dbTime();var command=lab.backend.learning.identityaccess.AdmissionWire.fence(req.getInputStream().readNBytes(8193),op);
        respond(response,200,gate.fence(command).json());return;
      }
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
