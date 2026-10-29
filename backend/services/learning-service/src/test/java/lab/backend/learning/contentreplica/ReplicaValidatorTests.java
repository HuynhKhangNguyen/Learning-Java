package lab.backend.learning.contentreplica;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReplicaValidatorTests {
  private final ReplicaValidator validator = new ReplicaValidator();
  private final byte[] bundle = bytes(
      "{\"schemaVersion\":2,\"id\":\"CASE-X\",\"unitId\":\"UNIT-X\","
          + "\"expected\":{\"sentinel\":\"PRIVATE\"},"
          + "\"rubricTargets\":[\"CAP-COL-002-A\"]}");
  private final byte[] manifest = bytes(
      "[{\"capabilityId\":\"CAP-COL-002-A\",\"version\":\"1\","
          + "\"newAttemptAllowed\":true,\"submitAllowed\":true,"
          + "\"finalizeAllowed\":true,\"currentEvidencePolicy\":\"RETAIN\","
          + "\"bodyAvailable\":true,\"reason\":\"cosmetic\"}]");

  @Test
  void verifiesAndDefensivelyCopiesExactPrivateInputs() {
    var verified = verify(bundle, manifest);
    verified.bundle()[0] = 0;
    assertEquals('{', verified.bundle()[0]);
  }

  @Test
  void rejectsDigestTruncationUnknownFieldsAndDuplicateManifestKeys() {
    assertThrows(IllegalArgumentException.class,
        () -> validator.verify(bundle, manifest, "0".repeat(64), digest(manifest)));
    byte[] unknown = replace(bundle, "\"schemaVersion\":2", "\"schemaVersion\":2,\"leak\":true");
    assertInvalid(unknown, manifest);
    String row = new String(manifest, StandardCharsets.UTF_8);
    byte[] duplicated = bytes("[" + row.substring(1, row.length() - 1) + "," + row.substring(1));
    assertInvalid(bundle, duplicated);
  }

  @Test
  void rejectsMalformedManifestFieldTypesBoundsAndPolicyTuples() {
    assertInvalid(bundle, replace(manifest, "\"version\":\"1\"", "\"version\":1"));
    assertInvalid(bundle, replace(manifest, "\"version\":\"1\"", "\"version\":\"bad version\""));
    assertInvalid(bundle, replace(manifest, "\"version\":\"1\"",
        "\"version\":\"" + "v".repeat(65) + "\""));
    for (String field : new String[]{"newAttemptAllowed", "submitAllowed", "finalizeAllowed",
        "bodyAvailable"}) {
      assertInvalid(bundle, replace(manifest, "\"" + field + "\":true",
          "\"" + field + "\":\"true\""));
    }
    assertInvalid(bundle, replace(manifest, "\"currentEvidencePolicy\":\"RETAIN\"",
        "\"currentEvidencePolicy\":1"));
    assertInvalid(bundle, replace(manifest, "\"reason\":\"cosmetic\"", "\"reason\":1"));
    assertInvalid(bundle, replace(manifest, "\"reason\":\"cosmetic\"", "\"reason\":\"   \""));
    assertInvalid(bundle, replace(manifest, "\"reason\":\"cosmetic\"",
        "\"reason\":\"" + "r".repeat(513) + "\""));
    assertInvalid(bundle, replace(manifest, "\"newAttemptAllowed\":true",
        "\"newAttemptAllowed\":false"));
    assertInvalid(bundle, bytes("[]"));
  }

  @Test
  void requiresIntegralAndBoundPrivateBundleV2Fields() {
    assertInvalid(replace(bundle, "\"schemaVersion\":2", "\"schemaVersion\":2.0"), manifest);
    assertInvalid(replace(bundle, "\"schemaVersion\":2", "\"schemaVersion\":\"2\""), manifest);
    assertInvalid(replace(bundle, "\"schemaVersion\":2", "\"schemaVersion\":4294967298"), manifest);
    assertInvalid(replace(bundle, "\"id\":\"CASE-X\"",
        "\"id\":\"CASE-" + "X".repeat(124) + "\""), manifest);
    assertInvalid(replace(bundle, "\"rubricTargets\":[\"CAP-COL-002-A\"]",
        "\"rubricTargets\":[\"CAP-COL-002-A\",\"CAP-COL-002-A\"]"), manifest);
  }

  private ReplicaValidator.Verified verify(byte[] privateBundle, byte[] policyManifest) {
    return validator.verify(privateBundle, policyManifest, digest(privateBundle), digest(policyManifest));
  }

  private void assertInvalid(byte[] privateBundle, byte[] policyManifest) {
    assertThrows(IllegalArgumentException.class, () -> verify(privateBundle, policyManifest));
  }

  private static byte[] replace(byte[] source, String oldValue, String newValue) {
    return bytes(new String(source, StandardCharsets.UTF_8).replace(oldValue, newValue));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String digest(byte[] value) {
    return PublicationWire.sha256(value);
  }
}
