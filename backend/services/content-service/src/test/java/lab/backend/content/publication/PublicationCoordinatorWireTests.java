package lab.backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class PublicationCoordinatorWireTests {
  private static final PublicationCoordinatorWire.Binding BINDING =
      new PublicationCoordinatorWire.Binding(
          UUID.fromString("00000000-0000-4000-8000-000000000061"),
          1,
          2,
          "fx05-p2",
          "a".repeat(64),
          "b".repeat(64),
          UUID.fromString("00000000-0000-4000-8000-000000000062"),
          UUID.fromString("00000000-0000-4000-8000-000000000063"),
          UUID.fromString("00000000-0000-4000-8000-000000000064"));

  @Test
  void exactCommandIsStableAndCorrectReceiptIsAccepted() throws Exception {
    var first = PublicationCoordinatorWire.command(BINDING, "FENCE", 2);
    var retry = PublicationCoordinatorWire.command(BINDING, "FENCE", 2);

    assertThat(retry.bytes()).containsExactly(first.bytes());
    assertThat(retry.digest()).isEqualTo(first.digest());
    PublicationCoordinatorWire.verify(receipt(first), BINDING, first);
  }

  @Test
  void everyAuthorityBindingAndUnknownFieldFailsClosed() throws Exception {
    var command = PublicationCoordinatorWire.command(BINDING, "RELEASE", 3);
    ObjectNode valid = (ObjectNode) WireCodec.JSON.readTree(receipt(command));
    for (String field : new String[] {
        "operationId", "phase", "state", "bundleSha256", "manifestSha256", "realmId",
        "producerGeneration", "participantGeneration", "commandSha256"
    }) {
      ObjectNode changed = valid.deepCopy();
      changed.put(field, field.equals("state") ? "FENCED" : "wrong");
      assertThatThrownBy(() -> PublicationCoordinatorWire.verify(
          WireCodec.JSON.writeValueAsBytes(changed), BINDING, command))
          .hasMessageContaining("PUBLICATION_RESPONSE");
    }
    ObjectNode extra = valid.deepCopy();
    extra.put("privateBundle", "must-not-cross-boundary");
    assertThatThrownBy(() -> PublicationCoordinatorWire.verify(
        WireCodec.JSON.writeValueAsBytes(extra), BINDING, command))
        .hasMessageContaining("SCHEMA_INVALID");
  }

  private static byte[] receipt(PublicationCoordinatorWire.Command command) {
    ObjectNode node = WireCodec.JSON.createObjectNode();
    node.put("contractVersion", 1);
    node.put("operationId", BINDING.operationId().toString());
    node.put("phase", command.phase());
    node.put("state", switch (command.phase()) {
      case "STAGE" -> "STAGED";
      case "FENCE" -> "FENCED";
      case "RELEASE" -> "RELEASED";
      case "ABORT" -> "ABORTED";
      default -> throw new IllegalArgumentException();
    });
    node.put("parentRevision", BINDING.parentRevision());
    node.put("targetRevision", BINDING.targetRevision());
    node.put("bundleId", BINDING.bundleId());
    node.put("bundleSha256", BINDING.bundleSha256());
    node.put("manifestSha256", BINDING.manifestSha256());
    node.put("realmId", BINDING.realmId().toString());
    node.put("producerGeneration", BINDING.producerGeneration().toString());
    node.put("participantGeneration", BINDING.participantGeneration().toString());
    node.put("deliverySequence", command.sequence());
    node.put("commandSha256", command.digest());
    return WireCodec.JSON.writeValueAsBytes(node);
  }
}
