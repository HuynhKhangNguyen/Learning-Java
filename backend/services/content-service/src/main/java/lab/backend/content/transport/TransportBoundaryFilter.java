package lab.backend.content.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import lab.backend.content.browser.BrowserHandler;
import lab.backend.content.delivery.DeliveryHttpHandler;
import lab.backend.content.identityaccess.AdmissionGate;
import lab.backend.content.identityaccess.AdmissionWire;
import lab.backend.content.publication.PublicationHttpHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces listener/caller/route boundaries before any domain handler runs. */
public final class TransportBoundaryFilter extends OncePerRequestFilter {
  private static final String PUBLIC = "/api/v1/transport/status";
  private static final String PRIVATE = "/internal/v1/transport/probes";

  private final TransportSettings settings;
  private final AdmissionGate gate;
  private final DeliveryHttpHandler delivery;
  private final PublicationHttpHandler publication;
  private final BrowserHandler browser;

  public TransportBoundaryFilter(TransportSettings settings) {
    this(settings, null);
  }

  public TransportBoundaryFilter(TransportSettings settings, AdmissionGate gate) {
    this(settings, gate, null);
  }

  public TransportBoundaryFilter(
      TransportSettings settings, AdmissionGate gate, DeliveryHttpHandler delivery) {
    this(settings, gate, delivery, null);
  }

  public TransportBoundaryFilter(
      TransportSettings settings,
      AdmissionGate gate,
      DeliveryHttpHandler delivery,
      PublicationHttpHandler publication) {
    this(settings, gate, delivery, publication, null);
  }

  public TransportBoundaryFilter(
      TransportSettings settings,
      AdmissionGate gate,
      DeliveryHttpHandler delivery,
      PublicationHttpHandler publication,
      BrowserHandler browser) {
    this.settings = settings;
    this.gate = gate;
    this.delivery = delivery;
    this.publication = publication;
    this.browser = browser;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain unused)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setContentType("application/json");
    try {
      X509Certificate[] chain = certificateChain(request);
      // Certificate identity, not the named port alone, decides the trust boundary.
      String caller = CertificateIdentity.identity(chain[0], "lab", true);
      int port = request.getLocalPort();
      boolean publicListener = port == settings.publicPort();
      if ((publicListener && !caller.equals("edge"))
          || (!publicListener && (port != settings.privatePort() || caller.equals("edge")))) {
        throw new WireCodec.Denied(403, "CALLER_DENIED");
      }
      rejectBrowserAuthorityHeaders(request);
      if (!publicListener
          && (request.getHeader("Origin") != null || request.getHeader("Cookie") != null)) {
        throw new WireCodec.Denied(403, "SERVICE_AUTH_REQUIRED");
      }
      String path = request.getRequestURI();
      if (path.contains("%")
          || path.contains(";")
          || path.contains("//")
          || path.contains("\\")
          || request.getQueryString() != null) {
        throw new WireCodec.Denied(400, "AMBIGUOUS_ROUTE");
      }
      if (publicListener) {
        handlePublic(request, response, path);
        return;
      }
      handlePrivate(request, response, caller, path);
    } catch (java.net.SocketTimeoutException timeout) {
      respond(response, 408, WireCodec.error("REQUEST_TIMEOUT"));
    } catch (WireCodec.Denied denied) {
      respond(response, denied.status, WireCodec.error(denied.code));
    } catch (IllegalStateException denied) {
      respond(response, 503, WireCodec.error("CLOCK_UNAVAILABLE"));
    }
  }

  private void handlePublic(
      HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
    if (browser != null && browser.handles(path)) {
      settings.dbTime();
      browser.handle(request, response);
      return;
    }
    if (!path.equals(PUBLIC)) {
      throw new WireCodec.Denied(404, "ROUTE_NOT_AVAILABLE");
    }
    if (!request.getMethod().equals("GET")) {
      throw new WireCodec.Denied(405, "METHOD_DENIED");
    }
    settings.dbTime();
    respond(
        response,
        200,
        Map.of(
            "schemaVersion", 1,
            "service", TransportSettings.SERVICE,
            "phase", "transport-foundation",
            "businessReady", false,
            "clockVerified", true));
  }

  private void handlePrivate(
      HttpServletRequest request, HttpServletResponse response, String caller, String path)
      throws IOException {
    if (delivery != null) {
      settings.dbTime();
      if (delivery.handle(request, response, caller)) {
        return;
      }
    }
    if (publication != null) {
      settings.dbTime();
      if (publication.handle(request, response, caller)) {
        return;
      }
    }
    if (gate != null && path.startsWith(AdmissionWire.FENCE_PATH)) {
      handleFence(request, response, caller, path);
      return;
    }
    if (!path.equals(PRIVATE)) {
      throw new WireCodec.Denied(404, "ROUTE_NOT_AVAILABLE");
    }
    if (!request.getMethod().equals("POST")) {
      throw new WireCodec.Denied(405, "METHOD_DENIED");
    }
    requireJson(request);
    WireCodec.Probe probe =
        WireCodec.probe(request.getInputStream().readNBytes(WireCodec.MAX_BYTES + 1));
    if (!probe.audience().equals(TransportSettings.SERVICE)
        || !allowed(caller, probe.action())) {
      throw new WireCodec.Denied(403, "ACTION_DENIED");
    }
    WireCodec.window(probe, settings.dbTime());
    respond(
        response,
        200,
        Map.of(
            "schemaVersion", 1,
            "service", TransportSettings.SERVICE,
            "phase", "transport-probe",
            "businessReady", false,
            "caller", caller,
            "expectedRevision", probe.revision(),
            "requestBinding", probe.binding()));
  }

  private void handleFence(
      HttpServletRequest request, HttpServletResponse response, String caller, String path)
      throws IOException {
    if (!caller.equals("identity")) {
      throw new WireCodec.Denied(403, "ACTION_DENIED");
    }
    if (!request.getMethod().equals("PUT")) {
      throw new WireCodec.Denied(405, "METHOD_DENIED");
    }
    requireJson(request);
    var operation =
        java.util.UUID.fromString(
            WireCodec.idempotencyKey(path.substring(AdmissionWire.FENCE_PATH.length())));
    settings.dbTime();
    var command = AdmissionWire.fence(request.getInputStream().readNBytes(8193), operation);
    respond(response, 200, gate.fence(command).json());
  }

  private static X509Certificate[] certificateChain(HttpServletRequest request) {
    Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
    if (!request.isSecure()
        || !(value instanceof X509Certificate[] chain)
        || chain.length == 0) {
      throw new WireCodec.Denied(403, "SERVICE_AUTH_REQUIRED");
    }
    return chain;
  }

  private static void rejectBrowserAuthorityHeaders(HttpServletRequest request) {
    for (String header :
        new String[] {
          "X-User-Id",
          "X-Owner-Id",
          "X-Caller",
          "X-Audience",
          "X-Authz-Context",
          "X-Forwarded-For",
          "X-Forwarded-Proto"
        }) {
      if (request.getHeader(header) != null) {
        throw new WireCodec.Denied(403, "UNTRUSTED_HEADER");
      }
    }
  }

  private static void requireJson(HttpServletRequest request) {
    String type = request.getContentType();
    if (type == null
        || !(type.equalsIgnoreCase("application/json")
            || type.equalsIgnoreCase("application/json;charset=utf-8")
            || type.equalsIgnoreCase("application/json; charset=utf-8"))) {
      throw new WireCodec.Denied(400, "BAD_CONTENT_TYPE");
    }
  }

  public static boolean allowed(String caller, String action) {
    if (action.equals("OPS_TRANSPORT")) {
      return caller.equals("operator");
    }
    return switch (TransportSettings.SERVICE) {
      case "identity" ->
          action.equals("ADMISSION_TRANSPORT") && Set.of("content", "learning").contains(caller);
      case "content" ->
          (action.equals("FENCE_TRANSPORT") && caller.equals("identity"))
              || (action.equals("BUNDLE_TRANSPORT") && caller.equals("learning"));
      case "learning" ->
          (action.equals("FENCE_TRANSPORT") && caller.equals("identity"))
              || (action.equals("PUBLICATION_TRANSPORT") && caller.equals("content"));
      default -> false;
    };
  }

  private static void respond(HttpServletResponse response, int status, Object body)
      throws IOException {
    response.setStatus(status);
    response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(body));
  }
}
