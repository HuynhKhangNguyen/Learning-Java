package lab.backend.content.browser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.backend.content.identityaccess.AdmissionWire;
import lab.backend.content.identityaccess.ContentAccess;
import lab.backend.content.transport.WireCodec;

/**
 * Adapter cho public API-C01–03; mọi success DTO được dựng bằng allowlist riêng.
 *
 * <p>Input gồm route/header đã qua transport boundary. Catalog/topic trả metadata
 * public; unit còn cần fresh admission. Output không serialize principal/private
 * publication bytes; wire ambiguity hoặc authorization failure trả bounded error.
 */
public class BrowserHandler {
  private static final String PREFIX = "/api/v1/topics/";
  private static final String COOKIE = "__Host-backend-lab-session";
  private final BrowserStore store;
  private final ContentAccess access;

  BrowserHandler(BrowserStore store, ContentAccess access) {
    this.store = store;
    this.access = access;
  }

  public boolean handles(String path) {
    return path.equals("/api/v1/catalog") || path.startsWith(PREFIX);
  }

  public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!request.getMethod().equals("GET")) {
      throw new WireCodec.Denied(405, "METHOD_DENIED");
    }
    if (request.getContentLengthLong() > 0
        || request.getContentType() != null
        || single(request, "Transfer-Encoding") != null) {
      throw new WireCodec.Denied(400, "BAD_CONTENT_TYPE");
    }
    String path = request.getRequestURI();
    if (path.equals("/api/v1/catalog")) {
      BrowserStore.View view = store.read();
      publicCache(response, view.canonical());
      respond(response, envelope(view, Map.of("roadmaps", catalog(view.snapshot()))));
      return;
    }
    String suffix = path.substring(PREFIX.length());
    String[] parts = suffix.split("/", -1);
    if (parts.length == 1) {
      String topicId = resource(parts[0]);
      BrowserStore.View view = store.read();
      BrowserSnapshot.Topic topic = view.snapshot().topic(topicId);
      publicCache(response, view.canonical());
      respond(response, envelope(view, Map.of("topic", outline(topic))));
      return;
    }
    if (parts.length == 3 && parts[1].equals("units")) {
      String topicId = resource(parts[0]);
      String unitId = resource(parts[2]);
      UUID context = context(single(request, "X-Session-Context"));
      String credential = cookie(request);
      AdmissionWire.Request admission =
          new AdmissionWire.Request(credential, context, topicId, unitId);
      Map<String, Object> result =
          authorize(
              admission,
              (connection, principal) -> {
                // ContentAccess giữ local account fence lock xuyên suốt read và
                // final deadline check; principal tuyệt đối không đi vào DTO.
                BrowserStore.View view = store.read(connection);
                BrowserSnapshot.Topic topic = view.snapshot().topic(topicId);
                BrowserSnapshot.Unit unit = view.snapshot().unit(topic, unitId);
                if (!topic.status().equals("PUBLISHED") || !unit.status().equals("PUBLISHED")) {
                  throw new WireCodec.Denied(404, "RESOURCE_NOT_FOUND");
                }
                return envelope(view, Map.of("unit", body(topic, unit)));
              });
      response.setHeader("Cache-Control", "private, no-store");
      respond(response, result);
      return;
    }
    throw new WireCodec.Denied(404, "RESOURCE_NOT_FOUND");
  }

  <T> T authorize(
      AdmissionWire.Request request,
      lab.backend.content.identityaccess.AdmissionGate.Work<T> work) {
    return access.read(request, work);
  }

  private static List<Map<String, Object>> catalog(BrowserSnapshot snapshot) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (BrowserSnapshot.Roadmap roadmap : snapshot.roadmaps()) {
      List<Map<String, Object>> topics =
          snapshot.topics().stream()
              .filter(topic -> roadmap.topicIds().contains(topic.id()))
              .map(BrowserHandler::catalogTopic)
              .toList();
      result.add(
          Map.ofEntries(
              Map.entry("id", roadmap.id()),
              Map.entry("title", roadmap.title()),
              Map.entry("status", roadmap.status()),
              Map.entry("topicIds", roadmap.topicIds()),
              Map.entry("placementTopicIds", roadmap.placementTopicIds()),
              Map.entry("topics", topics)));
    }
    return result;
  }

  private static Map<String, Object> catalogTopic(BrowserSnapshot.Topic topic) {
    return Map.of(
        "id", topic.id(),
        "title", topic.title(),
        "status", topic.status(),
        "capabilityIds", topic.capabilityIds());
  }

  private static Map<String, Object> outline(BrowserSnapshot.Topic topic) {
    List<Map<String, Object>> units = new ArrayList<>();
    for (BrowserSnapshot.Unit unit : topic.units()) {
      units.add(Map.of("id", unit.id(), "title", unit.title(), "status", unit.status()));
    }
    return Map.of(
        "id", topic.id(),
        "title", topic.title(),
        "status", topic.status(),
        "capabilityIds", topic.capabilityIds(),
        "prerequisiteTopicIds", topic.prerequisiteTopicIds(),
        "units", units);
  }

  private static Map<String, Object> body(
      BrowserSnapshot.Topic topic, BrowserSnapshot.Unit unit) {
    return Map.of(
        "id", unit.id(),
        "topicId", topic.id(),
        "title", unit.title(),
        "status", unit.status(),
        "blocks", unit.blocks().stream().map(BrowserSnapshot.Block::json).toList());
  }

  private static Map<String, Object> envelope(
      BrowserStore.View view, Map<String, Object> payload) {
    var result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", 1);
    result.put("canonicalRevision", Long.toString(view.canonical()));
    result.put("appliedRevision", Long.toString(view.applied()));
    result.put("asOf", format(view.asOf()));
    result.put("learningSyncPending", view.applied() < view.canonical());
    result.putAll(payload);
    return result;
  }

  private static String format(java.time.Instant instant) {
    return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
        .format(instant);
  }

  private static void publicCache(HttpServletResponse response, long revision) {
    response.setHeader("Cache-Control", "public, max-age=60, must-revalidate");
    response.setHeader("ETag", "\"content-r" + revision + "\"");
    response.setHeader("Vary", "Accept-Encoding");
  }

  private static String resource(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}")) {
      throw new WireCodec.Denied(400, "BAD_RESOURCE");
    }
    return value;
  }

  private static UUID context(String value) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw new IllegalArgumentException();
      }
      return UUID.fromString(value);
    } catch (IllegalArgumentException failure) {
      throw new WireCodec.Denied(401, "SESSION_INVALID");
    }
  }

  private static String cookie(HttpServletRequest request) {
    String line = single(request, "Cookie");
    if (line == null || line.length() > 4096) {
      throw new WireCodec.Denied(401, "SESSION_INVALID");
    }
    String value = null;
    for (String item : line.split(";", -1)) {
      String part = item.strip();
      int equals = part.indexOf('=');
      if (equals <= 0) {
        throw new WireCodec.Denied(400, "BAD_WIRE");
      }
      if (part.substring(0, equals).equals(COOKIE)) {
        if (value != null) {
          throw new WireCodec.Denied(400, "BAD_WIRE");
        }
        value = part.substring(equals + 1);
      }
    }
    if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) {
      throw new WireCodec.Denied(401, "SESSION_INVALID");
    }
    return value;
  }

  private static String single(HttpServletRequest request, String name) {
    var values = request.getHeaders(name);
    if (values == null || !values.hasMoreElements()) {
      return null;
    }
    String value = values.nextElement();
    if (values.hasMoreElements() || value.indexOf(',') >= 0) {
      throw new WireCodec.Denied(400, "BAD_WIRE");
    }
    return value;
  }

  private static void respond(HttpServletResponse response, Object body) throws IOException {
    response.setStatus(200);
    response.getOutputStream().write(WireCodec.JSON.writeValueAsBytes(body));
  }
}
