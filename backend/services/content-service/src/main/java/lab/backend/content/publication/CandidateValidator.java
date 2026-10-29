package lab.backend.content.publication;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Boundary strict cho FX-05.
 *
 * <p>Validator chỉ chứng minh cấu trúc, liên kết và policy machine-readable; nó
 * không biến schema PASS thành semantic-review PASS. Input chỉ được đọc và mọi
 * mảng byte trả ra đều được copy để caller không sửa candidate sau validate.
 */
public final class CandidateValidator {
  static final int MAX_CANDIDATE_BYTES = 524_288;
  static final int MAX_BUNDLE_BYTES = 1_048_576;
  static final int MAX_MANIFEST_BYTES = 262_144;
  // Giới hạn này giữ cùng một giá trị revision giữa Java long và JSON/JavaScript number.
  private static final long MAX_JSON_SAFE_INTEGER = 9_007_199_254_740_991L;
  private static final int MAX_COMPONENTS = 4_096;
  private static final int MAX_RELATIONS = 16_384;
  private static final int MAX_IMPACTS = 4_096;
  private static final int MAX_OLD_VERSIONS = 16_384;
  private static final String ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}";
  private static final String BUNDLE_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
  private static final String VERSION_PATTERN = "[A-Za-z0-9][A-Za-z0-9._+-]{0,63}";
  private static final String DIGEST_PATTERN = "[0-9a-f]{64}";
  private static final Set<String> ROOT = Set.of(
      "schemaVersion", "operationId", "parentRevision", "targetRevision", "bundle",
      "components", "references", "prerequisiteEdges", "coverage", "review", "impacts",
      "oldVersions");
  private static final List<String> REVIEW_SUBJECT_FIELDS = List.of(
      "schemaVersion", "operationId", "parentRevision", "targetRevision", "bundle",
      "components", "references", "prerequisiteEdges", "impacts", "oldVersions");
  private static final JsonMapper JSON = JsonMapper.builder(
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();

  /**
   * Một classification có đúng một tuple hợp lệ trong R06a. Đặt policy ở một
   * bảng giúp tránh các nhánh boolean rời rạc vô tình nhận tổ hợp mâu thuẫn.
   */
  private static final Map<String, ManifestPolicy> POLICY_BY_IMPACT = Map.of(
      "COSMETIC", new ManifestPolicy(true, true, true, "RETAIN", true),
      "REVERIFY", new ManifestPolicy(false, true, true, "REVERIFY", true),
      "WITHDRAWN", new ManifestPolicy(false, false, true, "WITHDRAW", true),
      "INVALID_OLD_RUBRIC", new ManifestPolicy(false, false, false, "REVIEW", true));

  public record Valid(
      UUID operationId,
      long parent,
      long target,
      String bundleId,
      String bundleDigest,
      byte[] candidateBytes,
      byte[] bundleBytes,
      byte[] manifestBytes) {
    public Valid {
      candidateBytes = candidateBytes.clone();
      bundleBytes = bundleBytes.clone();
      manifestBytes = manifestBytes.clone();
    }

    @Override public byte[] candidateBytes() { return candidateBytes.clone(); }
    @Override public byte[] bundleBytes() { return bundleBytes.clone(); }
    @Override public byte[] manifestBytes() { return manifestBytes.clone(); }
  }

  public Valid validate(byte[] candidate, byte[] bundle) {
    try {
      requireSize(candidate, 2, MAX_CANDIDATE_BYTES, "CANDIDATE_SIZE_INVALID");
      requireSize(bundle, 1, MAX_BUNDLE_BYTES, "BUNDLE_SIZE_INVALID");
      utf8(candidate);
      utf8(bundle);

      JsonNode privatePayload = JSON.readTree(bundle);
      exact(privatePayload, Set.of("schemaVersion", "id", "unitId", "expected", "rubricTargets"));
      if (!privatePayload.path("schemaVersion").isIntegralNumber()
          || privatePayload.path("schemaVersion").asInt() != 2
          || !privatePayload.path("expected").isObject()) {
        fail("PRIVATE_BUNDLE_INVALID");
      }

      JsonNode root = JSON.readTree(candidate);
      if (root == null || !root.isObject() || !names(root).equals(ROOT)) {
        fail("CANDIDATE_FIELDS_INVALID");
      }
      if (!root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt() != 1) {
        fail("SCHEMA_UNSUPPORTED");
      }

      String operationText = boundedText(root, "operationId",
          "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}", 36);
      UUID operation = UUID.fromString(operationText);
      long parent = positive(root, "parentRevision");
      long target = positive(root, "targetRevision");
      if (target != parent + 1) fail("REVISION_INVALID");

      JsonNode bundleMetadata = root.path("bundle");
      exact(bundleMetadata, Set.of("id", "sha256"));
      String bundleId = boundedText(bundleMetadata, "id", BUNDLE_ID_PATTERN, 128);
      String bundleDigest = boundedText(bundleMetadata, "sha256", DIGEST_PATTERN, 64);
      if (!bundleDigest.equals(PublicationDigest.sha256(bundle))) fail("BUNDLE_DIGEST_MISMATCH");

      Map<String, Component> components = validateComponents(root);
      validatePrivatePayload(privatePayload, components, bundleDigest);
      validateReferences(root, components);
      validateDag(root, components.keySet());
      Map<String, Impact> impacts = validateImpacts(root, components);
      validateManifest(root, impacts, components);
      validateReports(root);

      byte[] manifest = JSON.writeValueAsBytes(root.path("oldVersions"));
      requireSize(manifest, 2, MAX_MANIFEST_BYTES, "MANIFEST_SIZE_INVALID");
      return new Valid(operation, parent, target, bundleId, bundleDigest, candidate, bundle, manifest);
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalArgumentException("CANDIDATE_INVALID", failure);
    }
  }

  private static void validatePrivatePayload(
      JsonNode payload, Map<String, Component> components, String bundleDigest) {
    String caseId = boundedText(payload, "id", "CASE-[A-Z0-9-]+", 128);
    String unitId = boundedText(payload, "unitId", "UNIT-[A-Z0-9-]+", 128);
    Component caseComponent = components.get(caseId);
    Component unitComponent = components.get(unitId);
    if (caseComponent == null || !"case".equals(caseComponent.type())
        || !bundleDigest.equals(caseComponent.digest())
        || unitComponent == null || !"content".equals(unitComponent.type())) {
      fail("PRIVATE_BUNDLE_BINDING_INVALID");
    }
    Set<String> rubricTargets = new HashSet<>();
    for (JsonNode target : array(payload, "rubricTargets", MAX_COMPONENTS)) {
      if (!target.isString() || !target.asString().matches("CAP-[A-Z]+-[0-9]{3}-[A-Z]")
          || !rubricTargets.add(target.asString())
          || !components.containsKey(target.asString())
          || !"rubric".equals(components.get(target.asString()).type())) {
        fail("PRIVATE_BUNDLE_BINDING_INVALID");
      }
    }
  }

  private static Map<String, Component> validateComponents(JsonNode root) {
    Map<String, Component> components = new HashMap<>();
    for (JsonNode component : array(root, "components", MAX_COMPONENTS)) {
      exact(component, Set.of("id", "type", "version", "sha256"));
      String id = boundedText(component, "id", ID_PATTERN, 128);
      String type = boundedText(component, "type", null, 16);
      String version = boundedText(component, "version", VERSION_PATTERN, 64);
      String digest = boundedText(component, "sha256", DIGEST_PATTERN, 64);
      if (!Set.of("catalog", "content", "rubric", "case").contains(type)
          || components.putIfAbsent(id, new Component(type, version, digest)) != null) {
        fail("COMPONENT_INVALID");
      }
    }
    return components;
  }

  private static void validateReferences(JsonNode root, Map<String, Component> components) {
    Set<String> references = new HashSet<>();
    for (JsonNode reference : array(root, "references", MAX_RELATIONS)) {
      exact(reference, Set.of("from", "to", "expectedType"));
      String from = boundedText(reference, "from", ID_PATTERN, 128);
      String to = boundedText(reference, "to", ID_PATTERN, 128);
      String expectedType = boundedText(reference, "expectedType", null, 16);
      String key = from + "\n" + to + "\n" + expectedType;
      if (!components.containsKey(from) || !components.containsKey(to)
          || !Objects.equals(components.get(to).type(), expectedType)
          || !references.add(key)) {
        fail("REFERENCE_INVALID");
      }
    }
  }

  private static void validateDag(JsonNode root, Set<String> componentIds) {
    Map<String, Set<String>> graph = new HashMap<>();
    for (JsonNode edge : array(root, "prerequisiteEdges", MAX_RELATIONS)) {
      exact(edge, Set.of("from", "to"));
      String from = boundedText(edge, "from", ID_PATTERN, 128);
      String to = boundedText(edge, "to", ID_PATTERN, 128);
      if (from.equals(to) || !componentIds.contains(from) || !componentIds.contains(to)
          || !graph.computeIfAbsent(from, ignored -> new HashSet<>()).add(to)) {
        fail("DAG_INVALID");
      }
    }
    Set<String> visited = new HashSet<>();
    for (String node : graph.keySet()) visit(node, graph, visited, new HashSet<>());
  }

  private static Map<String, Impact> validateImpacts(
      JsonNode root, Map<String, Component> components) {
    Map<String, Impact> impacts = new HashMap<>();
    for (JsonNode impact : array(root, "impacts", MAX_IMPACTS)) {
      exact(impact, Set.of("capabilityId", "classification", "affectedVersions"));
      String capability = boundedText(impact, "capabilityId", ID_PATTERN, 128);
      String classification = boundedText(impact, "classification", null, 32);
      Set<String> affectedVersions = new HashSet<>();
      for (JsonNode versionNode : array(impact, "affectedVersions", MAX_OLD_VERSIONS)) {
        if (!versionNode.isString() || !versionNode.asString().matches(VERSION_PATTERN)
            || !affectedVersions.add(versionNode.asString())) {
          fail("IMPACT_INVALID");
        }
      }
      Component component = components.get(capability);
      if (component == null || !"rubric".equals(component.type())
          || affectedVersions.contains(component.version())
          || !POLICY_BY_IMPACT.containsKey(classification)
          || impacts.putIfAbsent(capability, new Impact(classification, affectedVersions)) != null) {
        fail("IMPACT_INVALID");
      }
    }
    return impacts;
  }

  private static void validateManifest(
      JsonNode root, Map<String, Impact> impacts, Map<String, Component> components) {
    Set<String> expectedKeys = new HashSet<>();
    impacts.forEach((capability, impact) -> impact.affectedVersions()
        .forEach(version -> expectedKeys.add(manifestKey(capability, version))));
    Set<String> actualKeys = new HashSet<>();

    for (JsonNode manifest : array(root, "oldVersions", MAX_OLD_VERSIONS)) {
      exact(manifest, Set.of("capabilityId", "version", "newAttemptAllowed", "submitAllowed",
          "finalizeAllowed", "currentEvidencePolicy", "bodyAvailable", "reason"));
      String capability = boundedText(manifest, "capabilityId", ID_PATTERN, 128);
      String version = boundedText(manifest, "version", VERSION_PATTERN, 64);
      String key = manifestKey(capability, version);
      Impact impact = impacts.get(capability);
      if (impact == null || !expectedKeys.contains(key) || !actualKeys.add(key)
          || version.equals(components.get(capability).version())) {
        fail("MANIFEST_INVALID");
      }
      boundedText(manifest, "reason", null, 512);
      ManifestPolicy actual = new ManifestPolicy(
          requiredBoolean(manifest, "newAttemptAllowed"),
          requiredBoolean(manifest, "submitAllowed"),
          requiredBoolean(manifest, "finalizeAllowed"),
          boundedText(manifest, "currentEvidencePolicy", null, 16),
          requiredBoolean(manifest, "bodyAvailable"));
      if (!actual.equals(POLICY_BY_IMPACT.get(impact.classification()))) {
        fail("MANIFEST_POLICY_CONTRADICTION");
      }
    }
    if (!actualKeys.equals(expectedKeys)) fail("MANIFEST_INVALID");
  }

  private static void validateReports(JsonNode root) {
    JsonNode coverage = root.path("coverage");
    JsonNode review = root.path("review");
    Set<String> reportFields = Set.of("reportId", "subjectVersion", "subjectSha256", "status");
    exact(coverage, reportFields);
    exact(review, reportFields);
    String coverageId = boundedText(coverage, "reportId", "coverage-[A-Za-z0-9][A-Za-z0-9._:-]*", 128);
    String reviewId = boundedText(review, "reportId", "review-[A-Za-z0-9][A-Za-z0-9._:-]*", 128);
    if (coverageId.equals(reviewId)
        || !coverage.path("subjectVersion").isIntegralNumber()
        || coverage.path("subjectVersion").asInt() != 1
        || !review.path("subjectVersion").isIntegralNumber()
        || review.path("subjectVersion").asInt() != 1
        || !"PASSED".equals(boundedText(coverage, "status", null, 16))
        || !"APPROVED".equals(boundedText(review, "status", null, 16))) {
      fail("REPORT_BINDING_INVALID");
    }
    String expectedDigest = reviewSubjectDigest(root);
    if (!boundedText(coverage, "subjectSha256", DIGEST_PATTERN, 64).equals(expectedDigest)
        || !boundedText(review, "subjectSha256", DIGEST_PATTERN, 64).equals(expectedDigest)) {
      fail("REPORT_BINDING_INVALID");
    }
  }

  /**
   * Tạo review subject không chứa chính coverage/review để tránh self-digest
   * vòng tròn. Object keys được sort; các tập metadata được sort theo semantic
   * key, nên đổi thứ tự trình bày không vô hiệu approval nhưng đổi nghĩa thì có.
   */
  static String reviewSubjectDigest(JsonNode candidate) {
    ObjectNode subject = JSON.createObjectNode();
    for (String field : REVIEW_SUBJECT_FIELDS) {
      JsonNode value = candidate.get(field);
      if (value == null) fail("REPORT_BINDING_INVALID");
      subject.set(field, canonicalSubjectValue(field, value));
    }
    StringBuilder canonical = new StringBuilder();
    appendCanonicalJson(subject, canonical);
    byte[] domainSeparated = ("backend-lab-publication-review-subject-v1\n" + canonical)
        .getBytes(StandardCharsets.UTF_8);
    return PublicationDigest.sha256(domainSeparated);
  }

  private static JsonNode canonicalSubjectValue(String field, JsonNode value) {
    Comparator<JsonNode> comparator = switch (field) {
      case "components" -> Comparator.comparing(node -> node.path("id").asString());
      case "references" -> Comparator.comparing(node -> node.path("from").asString()
          + "\n" + node.path("to").asString() + "\n" + node.path("expectedType").asString());
      case "prerequisiteEdges" -> Comparator.comparing(node -> node.path("from").asString()
          + "\n" + node.path("to").asString());
      case "impacts" -> Comparator.comparing(node -> node.path("capabilityId").asString());
      case "oldVersions" -> Comparator.comparing(node -> node.path("capabilityId").asString()
          + "\n" + node.path("version").asString());
      default -> null;
    };
    if (comparator == null || !value.isArray()) return value;
    List<JsonNode> sorted = new ArrayList<>();
    value.forEach(node -> sorted.add("impacts".equals(field) ? canonicalImpact(node) : node));
    sorted.sort(comparator);
    ArrayNode result = JSON.createArrayNode();
    sorted.forEach(result::add);
    return result;
  }

  private static JsonNode canonicalImpact(JsonNode impact) {
    ObjectNode copy = JSON.createObjectNode();
    impact.properties().forEach(property -> copy.set(property.getKey(), property.getValue()));
    JsonNode versions = impact.get("affectedVersions");
    if (versions != null && versions.isArray()) {
      List<String> sortedVersions = new ArrayList<>();
      versions.forEach(version -> sortedVersions.add(version.asString()));
      sortedVersions.sort(String::compareTo);
      ArrayNode canonicalVersions = JSON.createArrayNode();
      sortedVersions.forEach(canonicalVersions::add);
      copy.set("affectedVersions", canonicalVersions);
    }
    return copy;
  }

  private static void appendCanonicalJson(JsonNode node, StringBuilder output) {
    if (node.isObject()) {
      output.append('{');
      boolean first = true;
      for (String name : new TreeSet<>(names(node))) {
        if (!first) output.append(',');
        first = false;
        appendJsonString(name, output);
        output.append(':');
        appendCanonicalJson(node.get(name), output);
      }
      output.append('}');
    } else if (node.isArray()) {
      output.append('[');
      boolean first = true;
      for (JsonNode element : node) {
        if (!first) output.append(',');
        first = false;
        appendCanonicalJson(element, output);
      }
      output.append(']');
    } else if (node.isString()) {
      appendJsonString(node.asString(), output);
    } else if (node.isIntegralNumber()) {
      output.append(node.bigIntegerValue());
    } else if (node.isBoolean()) {
      output.append(node.asBoolean() ? "true" : "false");
    } else {
      fail("REVIEW_SUBJECT_INVALID");
    }
  }

  private static void appendJsonString(String value, StringBuilder output) {
    try {
      output.append(JSON.writeValueAsString(value));
    } catch (Exception failure) {
      throw new IllegalArgumentException("REVIEW_SUBJECT_INVALID", failure);
    }
  }

  private static void visit(
      String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> stack) {
    if (!stack.add(node)) fail("DAG_INVALID");
    if (visited.add(node)) {
      for (String next : graph.getOrDefault(node, Set.of())) visit(next, graph, visited, stack);
    }
    stack.remove(node);
  }

  private static void utf8(byte[] bytes) throws CharacterCodingException {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes));
  }

  private static Set<String> names(JsonNode node) {
    Set<String> result = new HashSet<>();
    node.properties().forEach(property -> result.add(property.getKey()));
    return result;
  }

  private static void exact(JsonNode node, Set<String> expected) {
    if (!node.isObject() || !names(node).equals(expected)) fail("FIELDS_INVALID");
  }

  private static String boundedText(JsonNode node, String field, String pattern, int maxLength) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString() || value.asString().isBlank()
        || value.asString().length() > maxLength
        || (pattern != null && !value.asString().matches(pattern))) {
      fail("FIELD_INVALID");
    }
    return value.asString();
  }

  private static long positive(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
        || value.asLong() < 1 || value.asLong() > MAX_JSON_SAFE_INTEGER) fail("FIELD_INVALID");
    return value.asLong();
  }

  private static boolean requiredBoolean(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isBoolean()) fail("FIELD_INVALID");
    return value.asBoolean();
  }

  private static List<JsonNode> array(JsonNode node, String field, int maxItems) {
    JsonNode value = node.get(field);
    if (value == null || !value.isArray() || value.isEmpty() || value.size() > maxItems) {
      fail("FIELD_INVALID");
    }
    List<JsonNode> result = new ArrayList<>(value.size());
    value.forEach(result::add);
    return result;
  }

  private static void requireSize(byte[] value, int minimum, int maximum, String error) {
    if (value == null || value.length < minimum || value.length > maximum) fail(error);
  }

  private static String manifestKey(String capability, String version) {
    return capability + "\n" + version;
  }

  private static void fail(String code) {
    throw new IllegalArgumentException(code);
  }

  private record Component(String type, String version, String digest) {}
  private record Impact(String classification, Set<String> affectedVersions) {}
  private record ManifestPolicy(
      boolean newAttemptAllowed,
      boolean submitAllowed,
      boolean finalizeAllowed,
      String currentEvidencePolicy,
      boolean bodyAvailable) {}
}
