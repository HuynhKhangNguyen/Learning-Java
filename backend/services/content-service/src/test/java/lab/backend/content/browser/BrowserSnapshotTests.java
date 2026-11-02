package lab.backend.content.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kiểm parser ingestion chỉ nhận public snapshot có schema và quan hệ an toàn.
 *
 * <p>Mỗi mutation là input bất biến riêng; output hoặc là DTO canonical, hoặc
 * bounded denial. Test đặc biệt giữ unknown/private field và unsafe block ngoài DTO.
 */
class BrowserSnapshotTests {
  private static byte[] fixture() throws Exception {
    return Files.readAllBytes(
        Path.of(
            BrowserSnapshotTests.class
                .getResource("/contracts/content/public/fixtures/browser-snapshot-v1.json")
                .toURI()));
  }

  @Test
  void parsesTwoRoadmapsSharedTopicAliasAndPlannedOutline() throws Exception {
    BrowserSnapshot snapshot = BrowserSnapshot.parse(fixture());
    assertThat(snapshot.roadmaps()).hasSize(2);
    assertThat(snapshot.topic("java-start").id()).isEqualTo("TOP-JAVA-01");
    assertThat(snapshot.topic("TOP-OPS-01").status()).isEqualTo("PLANNED");
    assertThat(snapshot.topic("TOP-SHARED-01").capabilityIds())
        .containsExactly("CAP-SHARED-01");
  }

  @Test
  void rejectsPrivateUnknownFieldsInsteadOfFilteringAfterDeserialization() throws Exception {
    ObjectNode root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    ((ObjectNode) root.path("topics").get(0)).put("expected", "PRIVATE-SENTINEL");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_SCHEMA_UNSUPPORTED");
  }

  @Test
  void rejectsRawScriptAndUnsafeUrlSchemes() throws Exception {
    ObjectNode root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    ObjectNode block =
        (ObjectNode)
            root.path("topics").get(0).path("units").get(0).path("blocks").get(0);
    block.put("text", "<script>alert(1)</script>");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_UNSAFE");

    root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    block = (ObjectNode) root.path("topics").get(0).path("units").get(0).path("blocks").get(0);
    block.put("text", "<img src=x onerror=alert(1)>");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_UNSAFE");

    root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    block = (ObjectNode) root.path("topics").get(0).path("units").get(0).path("blocks").get(0);
    block.put("url", "javascript:alert(1)");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_UNSAFE");
  }

  @Test
  void rejectsAmbiguousAliasAndBrokenPrerequisite() throws Exception {
    ObjectNode root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    ((ObjectNode) root.path("topics").get(1)).withArray("aliases").add("java-start");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_INVALID");

    root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    ((ObjectNode) root.path("topics").get(2)).withArray("prerequisiteTopicIds").add("TOP-MISSING");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_INVALID");
  }

  @Test
  void rejectsPlacementOutsideItsOwningRoadmap() throws Exception {
    ObjectNode root = (ObjectNode) WireCodec.JSON.readTree(fixture());
    ObjectNode javaRoadmap = (ObjectNode) root.path("roadmaps").get(0);
    javaRoadmap.withArray("placementTopicIds").removeAll().add("TOP-OPS-01");
    assertDenied(WireCodec.JSON.writeValueAsBytes(root), "BROWSER_SNAPSHOT_INVALID");
  }

  @Test
  void missingTopicAndUnitUseNonOracleNotFound() throws Exception {
    BrowserSnapshot snapshot = BrowserSnapshot.parse(fixture());
    assertThatThrownBy(() -> snapshot.topic("TOP-PRIVATE"))
        .isInstanceOfSatisfying(
            WireCodec.Denied.class,
            denied -> {
              assertThat(denied.status).isEqualTo(404);
              assertThat(denied.code).isEqualTo("RESOURCE_NOT_FOUND");
            });
    assertThatThrownBy(
            () -> snapshot.unit(snapshot.topic("TOP-JAVA-01"), "UNIT-PRIVATE"))
        .isInstanceOfSatisfying(
            WireCodec.Denied.class,
            denied -> assertThat(denied.code).isEqualTo("RESOURCE_NOT_FOUND"));
  }

  private static void assertDenied(byte[] bytes, String code) {
    assertThatThrownBy(() -> BrowserSnapshot.parse(bytes))
        .isInstanceOfSatisfying(
            WireCodec.Denied.class,
            denied -> {
              assertThat(denied.status).isEqualTo(503);
              assertThat(denied.code).isEqualTo(code);
            });
  }
}
