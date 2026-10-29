package lab.backend.content.publication;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lab.backend.content.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Strict INT-04 consumer codec; receipt bytes never become authority before every binding matches. */
public final class PublicationCoordinatorWire {
  public static final int MAX_BYTES = 8192;
  private static final Set<String> RESPONSE_FIELDS = Set.of(
      "contractVersion", "operationId", "phase", "state", "parentRevision",
      "targetRevision", "bundleId", "bundleSha256", "manifestSha256", "realmId",
      "producerGeneration", "participantGeneration", "deliverySequence", "commandSha256");
  private static final Map<String, String> STATES = Map.of(
      "STAGE", "STAGED",
      "FENCE", "FENCED",
      "RELEASE", "RELEASED",
      "ABORT", "ABORTED");

  public record Binding(
      UUID operationId,
      long parentRevision,
      long targetRevision,
      String bundleId,
      String bundleSha256,
      String manifestSha256,
      UUID realmId,
      UUID producerGeneration,
      UUID participantGeneration) {}

  public record Command(String phase, long sequence, byte[] bytes, String digest) {
    public Command {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  public static Command command(Binding binding, String phase, long sequence) {
    if (!STATES.containsKey(phase) || sequence < 1 || sequence > 9_007_199_254_740_991L) {
      throw denied("PUBLICATION_PHASE_INVALID");
    }
    LinkedHashMap<String, Object> json = new LinkedHashMap<>();
    json.put("contractVersion", 1);
    json.put("operationId", binding.operationId().toString());
    json.put("phase", phase);
    json.put("parentRevision", binding.parentRevision());
    json.put("targetRevision", binding.targetRevision());
    json.put("bundleId", binding.bundleId());
    json.put("bundleSha256", binding.bundleSha256());
    json.put("manifestSha256", binding.manifestSha256());
    json.put("realmId", binding.realmId().toString());
    json.put("producerGeneration", binding.producerGeneration().toString());
    json.put("participantGeneration", binding.participantGeneration().toString());
    json.put("deliverySequence", sequence);
    byte[] bytes = WireCodec.JSON.writeValueAsBytes(json);
    if (bytes.length > MAX_BYTES) {
      throw denied("PUBLICATION_COMMAND_TOO_LARGE");
    }
    return new Command(phase, sequence, bytes, PublicationDigest.sha256(bytes));
  }

  public static void verify(byte[] bytes, Binding expected, Command command) {
    if (bytes.length < 2 || bytes.length > MAX_BYTES) {
      throw denied("PUBLICATION_RESPONSE_SIZE_INVALID");
    }
    JsonNode node = WireCodec.strict(bytes);
    HashSet<String> names = new HashSet<>();
    node.properties().forEach(property -> names.add(property.getKey()));
    if (!names.equals(RESPONSE_FIELDS)
        || !node.path("contractVersion").isInt()
        || node.path("contractVersion").asInt() != 1) {
      throw denied("PUBLICATION_RESPONSE_SCHEMA_INVALID");
    }
    boolean matches = text(node, "operationId").equals(expected.operationId().toString())
        && text(node, "phase").equals(command.phase())
        && text(node, "state").equals(STATES.get(command.phase()))
        && safeLong(node, "parentRevision") == expected.parentRevision()
        && safeLong(node, "targetRevision") == expected.targetRevision()
        && text(node, "bundleId").equals(expected.bundleId())
        && text(node, "bundleSha256").equals(expected.bundleSha256())
        && text(node, "manifestSha256").equals(expected.manifestSha256())
        && text(node, "realmId").equals(expected.realmId().toString())
        && text(node, "producerGeneration").equals(expected.producerGeneration().toString())
        && text(node, "participantGeneration").equals(expected.participantGeneration().toString())
        && safeLong(node, "deliverySequence") == command.sequence()
        && text(node, "commandSha256").equals(command.digest());
    if (!matches) {
      throw denied("PUBLICATION_RESPONSE_BINDING_INVALID");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString()) {
      throw denied("PUBLICATION_RESPONSE_SCHEMA_INVALID");
    }
    return value.asString();
  }

  private static long safeLong(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw denied("PUBLICATION_RESPONSE_SCHEMA_INVALID");
    }
    long number = value.asLong();
    if (number < 1 || number > 9_007_199_254_740_991L) {
      throw denied("PUBLICATION_RESPONSE_SCHEMA_INVALID");
    }
    return number;
  }

  private static IllegalArgumentException denied(String code) {
    return new IllegalArgumentException(code);
  }

  private PublicationCoordinatorWire() {}
}
