package lab.backend.learning.work;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;
import lab.backend.learning.identityaccess.AdmissionWire;
import lab.backend.learning.transport.WireCodec;

/** Public L01/L02 adapter chỉ chạy sau verified edge mTLS; không nhận principal headers. */
public final class AllocationHttpHandler {
  private static final String BASE = "/api/v1/attempts";
  private final AllocationCoordinator coordinator;

  public AllocationHttpHandler(AllocationCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  public boolean handles(String path) {
    return path.equals(BASE) || path.startsWith(BASE + "/");
  }

  public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = request.getRequestURI();
    String credential = credential(single(request, "Cookie"));
    UUID context = UUID.fromString(WireCodec.idempotencyKey(single(request, "X-Session-Context")));
    java.util.Map<String, Object> result;
    if (path.equals(BASE)) {
      if (!request.getMethod().equals("POST")) throw AllocationWire.deny(405, "METHOD_DENIED");
      String contentType = request.getContentType();
      if (contentType == null || !java.util.Set.of("application/json",
          "application/json;charset=utf-8", "application/json; charset=utf-8")
          .contains(contentType.toLowerCase(java.util.Locale.ROOT))) {
        throw AllocationWire.deny(400, "BAD_CONTENT_TYPE");
      }
      UUID key = UUID.fromString(WireCodec.idempotencyKey(single(request, "Idempotency-Key")));
      String csrf = single(request, "X-CSRF-TOKEN");
      String origin = single(request, "Origin");
      byte[] body = request.getInputStream().readNBytes(8193);
      var validated = AllocationWire.create(body);
      result = coordinator.create(() -> AdmissionWire.Request.createAttempt(
          credential, context, csrf, origin, body), key, validated);
    } else {
      if (!request.getMethod().equals("GET")) throw AllocationWire.deny(405, "METHOD_DENIED");
      UUID attempt = UUID.fromString(WireCodec.idempotencyKey(path.substring(BASE.length() + 1)));
      if (request.getInputStream().read() != -1) throw AllocationWire.deny(400, "UNEXPECTED_BODY");
      result = coordinator.read(() -> AdmissionWire.Request.readAttempt(
          credential, context, attempt), attempt);
    }
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setContentType("application/json");
    response.setStatus(result.get("state").equals("preparing") ? 202 : 200);
    response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(result));
  }

  private static String single(HttpServletRequest request, String name) {
    Enumeration<String> values = request.getHeaders(name);
    if (values == null || !values.hasMoreElements()) throw AllocationWire.deny(401, "SESSION_INVALID");
    String value = values.nextElement();
    if (values.hasMoreElements() || value == null || value.length() > 8192) {
      throw AllocationWire.deny(400, "AMBIGUOUS_HEADER");
    }
    return value;
  }

  private static String credential(String cookie) {
    String found = null;
    for (String part : cookie.split(";", -1)) {
      String item = part.strip();
      if (item.startsWith("__Host-backend-lab-session=")) {
        if (found != null) throw AllocationWire.deny(401, "SESSION_INVALID");
        found = item.substring("__Host-backend-lab-session=".length());
      }
    }
    if (found == null || !found.matches("[A-Za-z0-9_-]{43}")) {
      throw AllocationWire.deny(401, "SESSION_INVALID");
    }
    return found;
  }
}
