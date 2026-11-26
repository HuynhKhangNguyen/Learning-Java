package lab.backend.learning.work;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import lab.backend.learning.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Body công khai exact allowlist; mọi owner/case/version chỉ lấy từ server. */
public final class AllocationWire {
  public record Create(String exerciseId, String requestedMode, String fingerprint) {}

  public static Create create(byte[] bytes) {
    if (bytes == null || bytes.length > 8192) {
      throw deny(413, "PAYLOAD_TOO_LARGE");
    }
    JsonNode node = WireCodec.strict(bytes);
    if (!node.isObject() || !node.propertyNames().equals(
        Set.of("schemaVersion", "exerciseId", "requestedMode"))
        || !node.path("schemaVersion").isInt() || node.path("schemaVersion").asInt() != 1) {
      throw deny(400, "UNSUPPORTED_SCHEMA");
    }
    String exercise = WireCodec.string(node, "exerciseId");
    String mode = WireCodec.string(node, "requestedMode");
    if (!exercise.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        || !Set.of("practice", "assessment", "project").contains(mode)) {
      throw deny(400, "BAD_ALLOCATION_REQUEST");
    }
    return new Create(exercise, mode, sha(("allocation-v1\n" + exercise + "\n" + mode)
        .getBytes(StandardCharsets.UTF_8)));
  }

  public static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception unavailable) {
      throw new IllegalStateException("SHA256 unavailable");
    }
  }

  static WireCodec.Denied deny(int status, String code) {
    return new WireCodec.Denied(status, code);
  }

  private AllocationWire() {}
}
