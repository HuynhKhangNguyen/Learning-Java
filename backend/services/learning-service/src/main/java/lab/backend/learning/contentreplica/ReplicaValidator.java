package lab.backend.learning.contentreplica;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Validates exact INT-03 private bytes and policy manifest before any database lock. */
public final class ReplicaValidator {
  private static final int MAX_RUBRIC_TARGETS = 4_096;
  private static final int MAX_MANIFEST_ROWS = 16_384;
  private static final String CAPABILITY_PATTERN = "CAP-[A-Z]+-[0-9]{3}-[A-Z]";
  private static final String VERSION_PATTERN = "[A-Za-z0-9][A-Za-z0-9._+-]{0,63}";
  private static final Set<ManifestPolicy> ALLOWED_POLICIES = Set.of(
      new ManifestPolicy(true, true, true, "RETAIN", true),
      new ManifestPolicy(false, true, true, "REVERIFY", true),
      new ManifestPolicy(false, false, true, "WITHDRAW", true),
      new ManifestPolicy(false, false, false, "REVIEW", true));

  public record Verified(byte[] bundle, byte[] manifest) {
    public Verified {
      bundle = bundle.clone();
      manifest = manifest.clone();
    }
    public byte[] bundle() { return bundle.clone(); }
    public byte[] manifest() { return manifest.clone(); }
  }

  public Verified verify(byte[] bundle, byte[] manifest, String bundleDigest, String manifestDigest) {
    if (bundle.length < 2 || bundle.length > 1_048_576
        || manifest.length < 2 || manifest.length > 262_144) fail("REPLICA_SIZE_INVALID");
    if (!PublicationWire.sha256(bundle).equals(bundleDigest)
        || !PublicationWire.sha256(manifest).equals(manifestDigest)) fail("REPLICA_DIGEST_MISMATCH");
    try {
      var decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      decoder.decode(ByteBuffer.wrap(bundle));
      decoder.reset();
      decoder.decode(ByteBuffer.wrap(manifest));

      Set<String> targets = validatePrivateBundle(PublicationWire.JSON.readTree(bundle));
      Set<String> capabilities = validateManifest(PublicationWire.JSON.readTree(manifest));
      if (!capabilities.containsAll(targets)) fail("PRIVATE_BUNDLE_BINDING_INVALID");
      return new Verified(bundle, manifest);
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalArgumentException("REPLICA_INVALID", failure);
    }
  }

  private static Set<String> validatePrivateBundle(JsonNode bundle) {
    if (bundle == null || !bundle.isObject()) fail("PRIVATE_BUNDLE_INVALID");
    JsonNode version = bundle.get("schemaVersion");
    if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
        || !Set.of(2, 3).contains(version.asInt())
        || !bundle.path("expected").isObject()) fail("PRIVATE_BUNDLE_INVALID");
    if (version.asInt() == 3) {
      exact(bundle, Set.of("schemaVersion", "id", "unitId", "expected", "rubricTargets", "allocation"));
      AllocationMetadataValidator.validate(bundle.get("allocation"));
    } else {
      exact(bundle, Set.of("schemaVersion", "id", "unitId", "expected", "rubricTargets"));
    }
    boundedText(bundle, "id", "CASE-[A-Z0-9-]+", 128);
    boundedText(bundle, "unitId", "UNIT-[A-Z0-9-]+", 128);
    JsonNode targetNodes = bundle.get("rubricTargets");
    if (targetNodes == null || !targetNodes.isArray() || targetNodes.isEmpty()
        || targetNodes.size() > MAX_RUBRIC_TARGETS) fail("PRIVATE_BUNDLE_INVALID");
    Set<String> targets = new HashSet<>();
    for (JsonNode target : targetNodes) {
      String capability = boundedText(target, CAPABILITY_PATTERN, 128);
      if (!targets.add(capability)) fail("PRIVATE_BUNDLE_INVALID");
    }
    return targets;
  }

  private static Set<String> validateManifest(JsonNode manifest) {
    if (manifest == null || !manifest.isArray() || manifest.isEmpty()
        || manifest.size() > MAX_MANIFEST_ROWS) fail("MANIFEST_INVALID");
    Set<String> keys = new HashSet<>(), capabilities = new HashSet<>();
    for (JsonNode row : manifest) {
      exact(row, Set.of("capabilityId", "version", "newAttemptAllowed", "submitAllowed",
          "finalizeAllowed", "currentEvidencePolicy", "bodyAvailable", "reason"));
      String capability = boundedText(row, "capabilityId", CAPABILITY_PATTERN, 128);
      String version = boundedText(row, "version", VERSION_PATTERN, 64);
      if (!keys.add(capability + "\n" + version)) fail("MANIFEST_INVALID");

      // Learning tự kiểm lại đúng bốn policy tuple của contract, không tin boolean rời từ Content.
      ManifestPolicy policy = new ManifestPolicy(
          requiredBoolean(row, "newAttemptAllowed"),
          requiredBoolean(row, "submitAllowed"),
          requiredBoolean(row, "finalizeAllowed"),
          boundedText(row, "currentEvidencePolicy", null, 16),
          requiredBoolean(row, "bodyAvailable"));
      boundedText(row, "reason", null, 512);
      if (!ALLOWED_POLICIES.contains(policy)) fail("MANIFEST_POLICY_CONTRADICTION");
      capabilities.add(capability);
    }
    return capabilities;
  }

  private static String boundedText(JsonNode object, String field, String pattern, int maxLength) {
    return boundedText(object == null ? null : object.get(field), pattern, maxLength);
  }

  private static String boundedText(JsonNode value, String pattern, int maxLength) {
    if (value == null || !value.isString() || value.asString().isBlank()
        || value.asString().length() > maxLength
        || (pattern != null && !value.asString().matches(pattern))) fail("FIELD_INVALID");
    return value.asString();
  }

  private static boolean requiredBoolean(JsonNode object, String field) {
    JsonNode value = object.get(field);
    if (value == null || !value.isBoolean()) fail("FIELD_INVALID");
    return value.asBoolean();
  }

  private static void exact(JsonNode node, Set<String> expected) {
    if (node == null || !node.isObject()) fail("FIELDS_INVALID");
    var actual = new HashSet<String>();
    actual.addAll(node.propertyNames());
    if (!actual.equals(expected)) fail("FIELDS_INVALID");
  }

  private static void fail(String code) { throw new IllegalArgumentException(code); }

  private record ManifestPolicy(boolean newAttemptAllowed, boolean submitAllowed,
      boolean finalizeAllowed, String currentEvidencePolicy, boolean bodyAvailable) {}
}
