package lab.backend.content.browser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lab.backend.content.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/**
 * Parse public projection do publication sở hữu thành các browser DTO allowlist.
 *
 * <p>Input là exact immutable snapshot bytes; output chỉ chứa roadmap/topic/unit
 * public. Unknown field, quan hệ sai hoặc block có executable markup/unsafe URL
 * đều bị từ chối trước khi runtime có thể serialize dữ liệu.
 */
final class BrowserSnapshot {
  private static final Set<String> ROOT = Set.of("schemaVersion", "roadmaps", "topics");
  private static final Set<String> ROADMAP =
      Set.of("id", "title", "status", "topicIds", "placementTopicIds");
  private static final Set<String> TOPIC =
      Set.of(
          "id",
          "aliases",
          "title",
          "status",
          "capabilityIds",
          "prerequisiteTopicIds",
          "units");
  private static final Set<String> UNIT = Set.of("id", "title", "status", "blocks");
  private static final Set<String> BLOCK = Set.of("type", "text", "language", "url");
  private static final Set<String> STATUS = Set.of("PUBLISHED", "PLANNED", "WITHDRAWN");
  private static final Set<String> BLOCK_TYPES = Set.of("paragraph", "heading", "code", "link");

  record Roadmap(
      String id,
      String title,
      String status,
      List<String> topicIds,
      List<String> placementTopicIds) {}

  record Block(String type, String text, String language, String url) {
    Map<String, Object> json() {
      var result = new java.util.LinkedHashMap<String, Object>();
      result.put("type", type);
      result.put("text", text);
      if (language != null) {
        result.put("language", language);
      }
      if (url != null) {
        result.put("url", url);
      }
      return result;
    }
  }

  record Unit(String id, String title, String status, List<Block> blocks) {}

  record Topic(
      String id,
      List<String> aliases,
      String title,
      String status,
      List<String> capabilityIds,
      List<String> prerequisiteTopicIds,
      List<Unit> units) {}

  private final List<Roadmap> roadmaps;
  private final List<Topic> topics;

  private BrowserSnapshot(List<Roadmap> roadmaps, List<Topic> topics) {
    this.roadmaps = List.copyOf(roadmaps);
    this.topics = List.copyOf(topics);
  }

  static BrowserSnapshot parse(byte[] bytes) {
    JsonNode root = WireCodec.strict(bytes);
    exact(root, ROOT);
    if (!root.path("schemaVersion").isInt() || root.path("schemaVersion").asInt() != 1) {
      throw denied("BROWSER_SNAPSHOT_SCHEMA_UNSUPPORTED");
    }
    List<Roadmap> roadmaps = roadmaps(root.path("roadmaps"));
    List<Topic> topics = topics(root.path("topics"));
    if (roadmaps.size() != 2) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    Set<String> topicIds = unique(topics.stream().map(Topic::id).toList());
    for (Roadmap roadmap : roadmaps) {
      if (!topicIds.containsAll(roadmap.topicIds())
          || !topicIds.containsAll(roadmap.placementTopicIds())
          || !new HashSet<>(roadmap.topicIds())
              .containsAll(roadmap.placementTopicIds())) {
        throw denied("BROWSER_SNAPSHOT_INVALID");
      }
    }
    for (Topic topic : topics) {
      if (!topicIds.containsAll(topic.prerequisiteTopicIds())) {
        throw denied("BROWSER_SNAPSHOT_INVALID");
      }
    }
    return new BrowserSnapshot(roadmaps, topics);
  }

  List<Roadmap> roadmaps() {
    return roadmaps;
  }

  List<Topic> topics() {
    return topics;
  }

  Topic topic(String requestedId) {
    List<Topic> matches =
        topics.stream()
            .filter(
                topic ->
                    topic.id().equals(requestedId)
                        || topic.aliases().contains(requestedId))
            .toList();
    if (matches.size() != 1) {
      throw new WireCodec.Denied(404, "RESOURCE_NOT_FOUND");
    }
    return matches.getFirst();
  }

  Unit unit(Topic topic, String unitId) {
    return topic.units().stream()
        .filter(unit -> unit.id().equals(unitId))
        .findFirst()
        .orElseThrow(() -> new WireCodec.Denied(404, "RESOURCE_NOT_FOUND"));
  }

  private static List<Roadmap> roadmaps(JsonNode nodes) {
    requireArray(nodes);
    List<Roadmap> result = new ArrayList<>();
    for (JsonNode node : nodes) {
      exact(node, ROADMAP);
      result.add(
          new Roadmap(
              id(node, "id"),
              text(node, "title", 160),
              status(node),
              ids(node.path("topicIds")),
              ids(node.path("placementTopicIds"))));
    }
    unique(result.stream().map(Roadmap::id).toList());
    return result;
  }

  private static List<Topic> topics(JsonNode nodes) {
    requireArray(nodes);
    List<Topic> result = new ArrayList<>();
    Set<String> aliases = new HashSet<>();
    for (JsonNode node : nodes) {
      exact(node, TOPIC);
      String id = id(node, "id");
      List<String> topicAliases = ids(node.path("aliases"));
      int aliasCount = aliases.size();
      aliases.addAll(topicAliases);
      if (aliases.size() != aliasCount + topicAliases.size() || topicAliases.contains(id)) {
        throw denied("BROWSER_SNAPSHOT_INVALID");
      }
      result.add(
          new Topic(
              id,
              topicAliases,
              text(node, "title", 160),
              status(node),
              ids(node.path("capabilityIds")),
              ids(node.path("prerequisiteTopicIds")),
              units(node.path("units"))));
    }
    Set<String> topicIds = unique(result.stream().map(Topic::id).toList());
    if (topicIds.stream().anyMatch(aliases::contains)) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    return result;
  }

  private static List<Unit> units(JsonNode nodes) {
    requireArray(nodes);
    List<Unit> result = new ArrayList<>();
    for (JsonNode node : nodes) {
      exact(node, UNIT);
      result.add(
          new Unit(
              id(node, "id"),
              text(node, "title", 160),
              status(node),
              blocks(node.path("blocks"))));
    }
    unique(result.stream().map(Unit::id).toList());
    return result;
  }

  private static List<Block> blocks(JsonNode nodes) {
    requireArray(nodes);
    List<Block> result = new ArrayList<>();
    for (JsonNode node : nodes) {
      exact(node, BLOCK);
      String type = text(node, "type", 20);
      if (!BLOCK_TYPES.contains(type)) {
        throw denied("BROWSER_SNAPSHOT_INVALID");
      }
      String text = text(node, "text", 32_000);
      // Chặn raw markup ngay lúc ingestion để browser không cần render HTML nguy hiểm.
      String lowered = text.toLowerCase(java.util.Locale.ROOT);
      if (text.indexOf('<') >= 0
          || text.indexOf('>') >= 0
          || lowered.contains("javascript:")) {
        throw denied("BROWSER_SNAPSHOT_UNSAFE");
      }
      String language = optional(node, "language", 40);
      String url = optional(node, "url", 2048);
      if (url != null) {
        try {
          String scheme = URI.create(url).getScheme();
          if (!Set.of("https", "http").contains(scheme)) {
            throw denied("BROWSER_SNAPSHOT_UNSAFE");
          }
        } catch (IllegalArgumentException failure) {
          throw denied("BROWSER_SNAPSHOT_UNSAFE");
        }
      }
      result.add(new Block(type, text, language, url));
    }
    return List.copyOf(result);
  }

  private static String status(JsonNode node) {
    String value = text(node, "status", 20);
    if (!STATUS.contains(value)) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    return value;
  }

  private static List<String> ids(JsonNode nodes) {
    requireArray(nodes);
    List<String> result = new ArrayList<>();
    for (JsonNode node : nodes) {
      if (!node.isString()) {
        throw denied("BROWSER_SNAPSHOT_INVALID");
      }
      result.add(validId(node.asString()));
    }
    unique(result);
    return List.copyOf(result);
  }

  private static String id(JsonNode node, String field) {
    return validId(text(node, field, 96));
  }

  private static String validId(String value) {
    if (!value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,95}")) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    return value;
  }

  private static String text(JsonNode node, String field, int maximum) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString() || value.asString().isBlank()) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    byte[] utf8 = value.asString().getBytes(StandardCharsets.UTF_8);
    if (utf8.length > maximum || value.asString().indexOf('\0') >= 0) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    return value.asString();
  }

  private static String optional(JsonNode node, String field, int maximum) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return text(node, field, maximum);
  }

  private static void exact(JsonNode node, Set<String> fields) {
    if (!node.isObject()) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    Set<String> actual = new HashSet<>();
    node.properties().forEach(property -> actual.add(property.getKey()));
    if (!actual.equals(fields)) {
      throw denied("BROWSER_SNAPSHOT_SCHEMA_UNSUPPORTED");
    }
  }

  private static void requireArray(JsonNode node) {
    if (!node.isArray()) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
  }

  private static Set<String> unique(List<String> values) {
    Set<String> result = new HashSet<>(values);
    if (result.size() != values.size()) {
      throw denied("BROWSER_SNAPSHOT_INVALID");
    }
    return result;
  }

  private static WireCodec.Denied denied(String code) {
    return new WireCodec.Denied(503, code);
  }
}
