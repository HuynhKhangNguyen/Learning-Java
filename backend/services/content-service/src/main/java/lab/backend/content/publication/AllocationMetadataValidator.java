package lab.backend.content.publication;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Kiểm metadata allocation v3 trước transaction; không trả private expected ra learner.
 * Hai service giữ validator riêng theo boundary, không chia sẻ runtime/domain JAR.
 */
final class AllocationMetadataValidator {
  private static final String STABLE = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";
  private static final String VERSION = "[A-Za-z0-9][A-Za-z0-9._+-]{0,63}";
  private static final Set<String> FIELDS = Set.of(
      "exerciseId", "mode", "caseVersion", "familyId", "exposureGroup", "equivalenceKey",
      "rubricVersion", "graderVersion", "prompt", "input", "inputSchema");
  private static final Set<String> TYPES = Set.of(
      "object", "array", "string", "integer", "number", "boolean", "null");
  private static final Set<String> SCHEMA_FIELDS = Set.of(
      "$schema", "type", "title", "description", "properties", "required", "additionalProperties",
      "items", "minItems", "maxItems", "uniqueItems", "minLength", "maxLength", "pattern",
      "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
      "minProperties", "maxProperties", "allOf", "anyOf", "oneOf", "not", "enum");

  static void validate(JsonNode allocation) {
    exact(allocation, FIELDS);
    for (String field : Set.of("exerciseId", "familyId", "exposureGroup", "equivalenceKey")) {
      text(allocation.get(field), STABLE, 128);
    }
    for (String field : Set.of("caseVersion", "rubricVersion", "graderVersion")) {
      text(allocation.get(field), VERSION, 64);
    }
    String mode = text(allocation.get("mode"), null, 16);
    if (!Set.of("practice", "assessment", "project").contains(mode)) fail();
    JsonNode prompt = allocation.get("prompt");
    exact(prompt, Set.of("en", "vi"));
    text(prompt.get("en"), null, 32768);
    text(prompt.get("vi"), null, 32768);
    if (!allocation.get("input").isObject() || !allocation.get("inputSchema").isObject()) fail();
    // Chỉ hỗ trợ structural draft-07 subset. Unknown keyword fail closed;
    // không resolve $ref/$id hay chạy plugin/regex trên dữ liệu learner ở bước này.
    schema(allocation.get("inputSchema"), 0, new int[]{0});
  }

  /** Ngăn rename/version/mode conflict làm known case trở lại clean. */
  static void consistent(JsonNode incoming, JsonNode prior) {
    if (incoming.path("schemaVersion").asInt() != 3
        || prior.path("schemaVersion").asInt() != 3) return;
    JsonNode next = incoming.path("allocation");
    JsonNode old = prior.path("allocation");
    if (incoming.path("id").equals(prior.path("id"))
        && next.path("caseVersion").equals(old.path("caseVersion"))
        && !incoming.equals(prior)) fail();
    if (next.path("familyId").equals(old.path("familyId"))) {
      for (String field : Set.of("equivalenceKey", "exposureGroup", "mode", "exerciseId")) {
        if (!next.path(field).equals(old.path(field))) fail();
      }
    }
    if (next.path("equivalenceKey").equals(old.path("equivalenceKey"))
        && !next.path("mode").equals(old.path("mode"))) fail();
    if (next.path("exerciseId").equals(old.path("exerciseId"))
        && next.path("exposureGroup").equals(old.path("exposureGroup"))
        && !next.path("mode").equals(old.path("mode"))) fail();
  }

  private static void schema(JsonNode node, int depth, int[] count) {
    if (depth > 16 || ++count[0] > 4096) fail();
    if (node.isBoolean()) return;
    if (!node.isObject()) fail();
    for (String key : node.propertyNames()) {
      if (!SCHEMA_FIELDS.contains(key)) fail();
      JsonNode value = node.get(key);
      switch (key) {
        case "$schema" -> {
          if (!value.isString()
              || !value.asString().equals("http://json-schema.org/draft-07/schema#")) fail();
        }
        case "title", "description", "pattern" -> text(value, null, 32768);
        case "type" -> {
          if (value.isString()) {
            if (!TYPES.contains(value.asString())) fail();
          } else {
            if (!value.isArray() || value.isEmpty()) fail();
            Set<String> types = new HashSet<>();
            for (JsonNode type : value) {
              if (!type.isString() || !TYPES.contains(type.asString())
                  || !types.add(type.asString())) fail();
            }
          }
        }
        case "properties" -> {
          if (!value.isObject()) fail();
          for (String property : value.propertyNames()) {
            textValue(property, 128);
            schema(value.get(property), depth + 1, count);
          }
        }
        case "required" -> {
          if (!value.isArray()) fail();
          Set<String> names = new HashSet<>();
          for (JsonNode name : value) {
            if (!name.isString() || !names.add(text(name, null, 128))) fail();
          }
        }
        case "additionalProperties", "not" -> schema(value, depth + 1, count);
        case "items" -> {
          if (value.isArray()) {
            if (value.isEmpty()) fail();
            for (JsonNode item : value) schema(item, depth + 1, count);
          } else schema(value, depth + 1, count);
        }
        case "allOf", "anyOf", "oneOf" -> {
          if (!value.isArray() || value.isEmpty()) fail();
          for (JsonNode item : value) schema(item, depth + 1, count);
        }
        case "uniqueItems" -> {
          if (!value.isBoolean()) fail();
        }
        case "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum" -> {
          if (!value.isNumber()) fail();
        }
        case "multipleOf" -> {
          if (!value.isNumber() || value.asDouble() <= 0) fail();
        }
        case "enum" -> {
          if (!value.isArray() || value.isEmpty()) fail();
          Set<JsonNode> values = new HashSet<>();
          for (JsonNode item : value) if (!values.add(item)) fail();
        }
        default -> {
          if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) fail();
        }
      }
    }
  }

  private static String text(JsonNode value, String pattern, int maximum) {
    if (value == null || !value.isString()) fail();
    String result = value.asString();
    textValue(result, maximum);
    if (pattern != null && !result.matches(pattern)) fail();
    return result;
  }

  private static void textValue(String value, int maximum) {
    if (value.isBlank() || value.length() > maximum) fail();
  }

  private static void exact(JsonNode node, Set<String> fields) {
    if (node == null || !node.isObject() || !node.propertyNames().equals(fields)) fail();
  }

  private static void fail() {
    throw new IllegalArgumentException("ALLOCATION_METADATA_INVALID");
  }

  private AllocationMetadataValidator() {}
}
