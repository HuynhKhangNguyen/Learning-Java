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
  private final lab.backend.learning.work.AllocationHttpHandler allocation;

  public TransportBoundaryFilter(TransportSettings settings) {
    this(settings, null);
  }

  public TransportBoundaryFilter(TransportSettings settings,
      lab.backend.learning.identityaccess.AdmissionGate gate) {
    this(settings, gate, null);
  }

  public TransportBoundaryFilter(TransportSettings settings,
      lab.backend.learning.identityaccess.AdmissionGate gate,
      lab.backend.learning.delivery.DeliveryHttpHandler delivery) {
    this(settings, gate, delivery, null);
  }

  public TransportBoundaryFilter(TransportSettings settings,
      lab.backend.learning.identityaccess.AdmissionGate gate,
      lab.backend.learning.delivery.DeliveryHttpHandler delivery,
      lab.backend.learning.contentreplica.PublicationHttpHandler publication) {
    this(settings, gate, delivery, publication, null);
  }

  public TransportBoundaryFilter(TransportSettings settings,
      lab.backend.learning.identityaccess.AdmissionGate gate,
      lab.backend.learning.delivery.DeliveryHttpHandler delivery,
      lab.backend.learning.contentreplica.PublicationHttpHandler publication,
      lab.backend.learning.work.AllocationHttpHandler allocation) {
    this.settings = settings;
    this.gate = gate;
    this.delivery = delivery;
    this.publication = publication;
    this.allocation = allocation;
  }
  private static final String PUBLIC="/api/v1/transport/status",PRIVATE="/internal/v1/transport/probes";
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain unused) throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setContentType("application/json");
    try {
      if (!request.isSecure()
          || !(request.getAttribute("jakarta.servlet.request.X509Certificate")
              instanceof X509Certificate[] chain) || chain.length == 0) {
        throw new WireCodec.Denied(403, "SERVICE_AUTH_REQUIRED");
      }
      // Named listener không thay certificate identity; public API chỉ nhận edge mTLS.
      String caller = CertificateIdentity.identity(chain[0], "lab", true);
      int port = request.getLocalPort();
      boolean publicListener = port == settings.publicPort();
      if ((publicListener && !caller.equals("edge"))
          || (!publicListener && (port != settings.privatePort() || caller.equals("edge")))) {
        throw new WireCodec.Denied(403, "CALLER_DENIED");
      }
      for (String header : new String[]{"X-User-Id", "X-Owner-Id", "X-Caller", "X-Audience",
          "X-Authz-Context", "X-Forwarded-For", "X-Forwarded-Proto"}) {
        if (request.getHeader(header) != null) throw new WireCodec.Denied(403, "UNTRUSTED_HEADER");
      }
      String path = request.getRequestURI();
      if (path.contains("%") || path.contains(";") || path.contains("//") || path.contains("\\")
          || request.getQueryString() != null) {
        throw new WireCodec.Denied(400, "AMBIGUOUS_ROUTE");
      }
      if (publicListener && allocation != null && allocation.handles(path)) {
        settings.dbTime();
        allocation.handle(request, response);
        return;
      }
      if (request.getHeader("Origin") != null
          || (!publicListener && request.getHeader("Cookie") != null)) {
        throw new WireCodec.Denied(403, "SERVICE_AUTH_REQUIRED");
      }
      if (publicListener) {
        if (!path.equals(PUBLIC)) throw new WireCodec.Denied(404, "ROUTE_NOT_AVAILABLE");
        if (!request.getMethod().equals("GET")) throw new WireCodec.Denied(405, "METHOD_DENIED");
        settings.dbTime();
        respond(response, 200, Map.of("schemaVersion", 1, "service", TransportSettings.SERVICE,
            "phase", "transport-foundation", "businessReady", false, "clockVerified", true));
        return;
      }
      if (delivery != null) {
        settings.dbTime();
        if (delivery.handle(request, response, caller)) return;
      }
      if (publication != null) {
        settings.dbTime();
        if (publication.handle(request, response, caller)) return;
      }
      if (gate != null && path.startsWith(lab.backend.learning.identityaccess.AdmissionWire.FENCE_PATH)) {
        if (!caller.equals("identity")) throw new WireCodec.Denied(403, "ACTION_DENIED");
        if (!request.getMethod().equals("PUT")) throw new WireCodec.Denied(405, "METHOD_DENIED");
        requireJson(request);
        var operation = java.util.UUID.fromString(WireCodec.idempotencyKey(path.substring(
            lab.backend.learning.identityaccess.AdmissionWire.FENCE_PATH.length())));
        settings.dbTime();
        var command = lab.backend.learning.identityaccess.AdmissionWire.fence(
            request.getInputStream().readNBytes(8193), operation);
        respond(response, 200, gate.fence(command).json());
        return;
      }
      if (!path.equals(PRIVATE)) throw new WireCodec.Denied(404, "ROUTE_NOT_AVAILABLE");
      if (!request.getMethod().equals("POST")) throw new WireCodec.Denied(405, "METHOD_DENIED");
      requireJson(request);
      WireCodec.Probe probe = WireCodec.probe(request.getInputStream().readNBytes(WireCodec.MAX_BYTES + 1));
      if (!probe.audience().equals(TransportSettings.SERVICE) || !allowed(caller, probe.action())) {
        throw new WireCodec.Denied(403, "ACTION_DENIED");
      }
      WireCodec.window(probe, settings.dbTime());
      respond(response, 200, Map.of("schemaVersion", 1, "service", TransportSettings.SERVICE,
          "phase", "transport-probe", "businessReady", false, "caller", caller,
          "expectedRevision", probe.revision(), "requestBinding", probe.binding()));
    } catch (java.net.SocketTimeoutException timeout) {
      respond(response, 408, WireCodec.error("REQUEST_TIMEOUT"));
    } catch (WireCodec.Denied denied) {
      respond(response, denied.status, WireCodec.error(denied.code));
    } catch (IllegalStateException unavailable) {
      respond(response, 503, WireCodec.error("CLOCK_UNAVAILABLE"));
    }
  }

  private static void requireJson(HttpServletRequest request) {
    String type = request.getContentType();
    if (type == null || !(type.equalsIgnoreCase("application/json")
        || type.equalsIgnoreCase("application/json;charset=utf-8")
        || type.equalsIgnoreCase("application/json; charset=utf-8"))) {
      throw new WireCodec.Denied(400, "BAD_CONTENT_TYPE");
    }
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
