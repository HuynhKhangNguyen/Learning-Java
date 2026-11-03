package lab.backend.learning.contentreplica;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import lab.backend.learning.transport.WireCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Exact fixture bytes dùng cho producer/consumer; không thay packaged publication proof. */
class AllocationV3CompatibilityTests {
  @Test
  void acceptsAuthoredV3AndHistoricalV2WithoutChangingBytes() throws Exception {
    byte[] v3 = fixture();
    verify(v3);
    verify(Files.readAllBytes(Path.of(
        "../../../contracts/fixtures/publication/fx05-private-bundle.json")));
  }

  @Test
  void missingUnknownConflictingMetadataAndSchemaEscapeFailClosed() throws Exception {
    for (String kind : new String[]{"missing", "unknown", "version", "mode", "ref", "default",
        "badSchema", "numericVersion", "unknownAllocation"}) {
      var bundle = (ObjectNode) WireCodec.JSON.readTree(fixture());
      var metadata = (ObjectNode) bundle.get("allocation");
      switch (kind) {
        case "missing" -> bundle.remove("allocation");
        case "unknown" -> bundle.put("owner", "forged");
        case "version" -> bundle.put("schemaVersion", 4);
        case "mode" -> metadata.put("mode", "clean");
        case "ref" -> metadata.set("inputSchema", WireCodec.JSON.readTree("{\"$ref\":\"https://untrusted.invalid\"}"));
        case "default" -> metadata.set("inputSchema", WireCodec.JSON.readTree("{\"default\":42}"));
        case "badSchema" -> metadata.set("inputSchema", WireCodec.JSON.readTree("{\"type\":\"wrong\"}"));
        case "numericVersion" -> bundle.put("schemaVersion", 3.0);
        case "unknownAllocation" -> metadata.put("passed", true);
        default -> fail("Unknown fixture");
      }
      byte[] bytes = WireCodec.JSON.writeValueAsBytes(bundle);
      assertThrows(IllegalArgumentException.class, () -> verify(bytes), kind);
    }
  }

  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(Path.of("../../../contracts/fixtures/exposure/r08-private-a-v3.json"));
  }

  private static void verify(byte[] bytes) throws Exception {
    byte[] manifest = WireCodec.JSON.writeValueAsBytes(WireCodec.JSON.readTree(Files.readAllBytes(
        Path.of("../../../contracts/fixtures/publication/fx05-candidate-cosmetic.json")))
        .path("oldVersions"));
    new ReplicaValidator().verify(bytes, manifest,
        PublicationWire.sha256(bytes), PublicationWire.sha256(manifest));
  }
}
