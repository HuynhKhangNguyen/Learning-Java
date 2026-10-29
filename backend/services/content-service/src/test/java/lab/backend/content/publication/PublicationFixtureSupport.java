package lab.backend.content.publication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import lab.backend.content.transport.WireCodec;

/** Test builder giữ mọi generated candidate bind đúng review subject mới. */
public final class PublicationFixtureSupport {
  private static final Path FIXTURES = Path.of("../../../contracts/fixtures/publication");

  public static byte[] candidateBytes() throws Exception {
    return Files.readAllBytes(FIXTURES.resolve("fx05-candidate-cosmetic.json"));
  }

  public static byte[] bundleBytes() throws Exception {
    return Files.readAllBytes(FIXTURES.resolve("fx05-private-bundle.json"));
  }

  public static ObjectNode candidateNode() throws Exception {
    return (ObjectNode) WireCodec.JSON.readTree(candidateBytes());
  }

  public static ObjectNode bundleNode() throws Exception {
    return (ObjectNode) WireCodec.JSON.readTree(bundleBytes());
  }

  public static byte[] mutateAndBind(Consumer<ObjectNode> mutation) throws Exception {
    ObjectNode candidate = candidateNode();
    mutation.accept(candidate);
    bind(candidate);
    return WireCodec.JSON.writeValueAsBytes(candidate);
  }

  public static byte[] serializeWithoutBinding(Consumer<ObjectNode> mutation) throws Exception {
    ObjectNode candidate = candidateNode();
    mutation.accept(candidate);
    return WireCodec.JSON.writeValueAsBytes(candidate);
  }

  public static CandidateValidator.Valid variant(
      UUID operationId, String bundleId, String expectedSentinel, String reason) throws Exception {
    ObjectNode bundle = bundleNode();
    ((ObjectNode) bundle.path("expected")).put("sentinel", expectedSentinel);
    byte[] bundleBytes = WireCodec.JSON.writeValueAsBytes(bundle);
    String bundleDigest = PublicationDigest.sha256(bundleBytes);
    byte[] candidateBytes = mutateAndBind(candidate -> {
      candidate.put("operationId", operationId.toString());
      ((ObjectNode) candidate.path("bundle")).put("id", bundleId).put("sha256", bundleDigest);
      for (JsonNode component : candidate.path("components")) {
        if (component.path("type").asString().equals("case")) {
          ((ObjectNode) component).put("sha256", bundleDigest);
        }
      }
      ((ObjectNode) candidate.path("oldVersions").get(0)).put("reason", reason);
    });
    return new CandidateValidator().validate(candidateBytes, bundleBytes);
  }

  public static byte[] candidateForBundle(byte[] bundleBytes, Consumer<ObjectNode> mutation)
      throws Exception {
    String digest = PublicationDigest.sha256(bundleBytes);
    return mutateAndBind(candidate -> {
      ((ObjectNode) candidate.path("bundle")).put("sha256", digest);
      for (JsonNode component : candidate.path("components")) {
        if (component.path("type").asString().equals("case")) {
          ((ObjectNode) component).put("sha256", digest);
        }
      }
      mutation.accept(candidate);
    });
  }

  public static void bind(ObjectNode candidate) {
    String digest = CandidateValidator.reviewSubjectDigest(candidate);
    ((ObjectNode) candidate.path("coverage")).put("subjectSha256", digest);
    ((ObjectNode) candidate.path("review")).put("subjectSha256", digest);
  }

  private PublicationFixtureSupport() {}
}
