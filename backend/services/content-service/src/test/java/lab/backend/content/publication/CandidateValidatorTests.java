package lab.backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** FX-05 boundary oracle; transaction/role/fault behavior thuộc CMD-03 PG suite. */
class CandidateValidatorTests {
  private final CandidateValidator validator = new CandidateValidator();
  @TempDir Path temporary;

  @Test
  void validCandidatePreservesInputAndUsesR05PrivateCaseContract() throws Exception {
    byte[] candidate = PublicationFixtureSupport.candidateBytes();
    byte[] before = candidate.clone();
    byte[] bundle = PublicationFixtureSupport.bundleBytes();

    CandidateValidator.Valid valid = validator.validate(candidate, bundle);

    assertThat(valid.bundleId()).isEqualTo("fx05-p2");
    assertThat(valid.parent()).isEqualTo(1);
    assertThat(valid.target()).isEqualTo(2);
    assertThat(candidate).containsExactly(before);
    assertThat(valid.bundleBytes()).containsExactly(bundle);
    JsonNode privateCase = WireCodec.JSON.readTree(bundle);
    assertThat(privateCase.path("schemaVersion").asInt()).isEqualTo(2);
    assertThat(privateCase.path("id").asString()).isEqualTo("CASE-CATALOG-FIXTURE");
    assertThat(privateCase.path("rubricTargets").get(0).asString()).isEqualTo("CAP-COL-002-A");
  }

  @Test
  void reviewSubjectBindsEverySemanticMetadataFieldAndValidatesReportIds() throws Exception {
    List<Consumer<ObjectNode>> semanticMutations = List.of(
        candidate -> ((ObjectNode) candidate.path("bundle")).put("id", "fx05-p2-renamed"),
        candidate -> ((ObjectNode) candidate.path("components").get(0)).put("version", "3"),
        candidate -> ((ObjectNode) candidate.path("references").get(0))
            .put("from", "CASE-CATALOG-FIXTURE"),
        candidate -> ((ObjectNode) candidate.path("prerequisiteEdges").get(0))
            .put("from", "CASE-CATALOG-FIXTURE"),
        candidate -> setPolicy(candidate, "REVERIFY", false, true, true, "REVERIFY", true),
        candidate -> ((ObjectNode) candidate.path("oldVersions").get(0))
            .put("reason", "Meaningful policy explanation changed"));

    for (Consumer<ObjectNode> mutation : semanticMutations) {
      byte[] staleApproval = PublicationFixtureSupport.serializeWithoutBinding(mutation);
      assertThatThrownBy(() -> validator.validate(staleApproval, PublicationFixtureSupport.bundleBytes()))
          .hasMessageContaining("REPORT_BINDING_INVALID");
      assertThat(validator.validate(
          PublicationFixtureSupport.mutateAndBind(mutation),
          PublicationFixtureSupport.bundleBytes()).target()).isEqualTo(2);
    }

    ObjectNode reordered = PublicationFixtureSupport.candidateNode();
    ArrayNode components = (ArrayNode) reordered.path("components");
    List<JsonNode> reversed = new ArrayList<>();
    components.forEach(reversed::add);
    Collections.reverse(reversed);
    components.removeAll();
    reversed.forEach(components::add);
    // Array semantic-set order không đổi digest, nên approval cũ vẫn đúng.
    assertThat(validator.validate(
        WireCodec.JSON.writeValueAsBytes(reordered), PublicationFixtureSupport.bundleBytes()).target())
        .isEqualTo(2);

    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("coverage")).put("reportId", "review-wrong-kind")),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("FIELD_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("review")).put("subjectVersion", 2)),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("REPORT_BINDING_INVALID");
  }

  @Test
  void manifestSupportsMultipleVersionsAndRejectsEveryPolicyContradiction() throws Exception {
    byte[] twoVersions = PublicationFixtureSupport.mutateAndBind(candidate -> {
      ((ArrayNode) candidate.path("impacts").get(0).path("affectedVersions")).add("0");
      ObjectNode second = (ObjectNode) candidate.path("oldVersions").get(0).deepCopy();
      second.put("version", "0").put("reason", "Older retained version");
      ((ArrayNode) candidate.path("oldVersions")).add(second);
    });
    assertThat(validator.validate(twoVersions, PublicationFixtureSupport.bundleBytes()).target())
        .isEqualTo(2);

    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate -> {
          ObjectNode duplicate = (ObjectNode) candidate.path("oldVersions").get(0).deepCopy();
          ((ArrayNode) candidate.path("oldVersions")).add(duplicate);
        }), PublicationFixtureSupport.bundleBytes())).hasMessageContaining("MANIFEST_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ArrayNode) candidate.path("impacts").get(0).path("affectedVersions")).add("0")),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("MANIFEST_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ArrayNode) candidate.path("impacts")).add(
                candidate.path("impacts").get(0).deepCopy())),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("IMPACT_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("impacts").get(0))
                .put("capabilityId", "CAP-COL-999-Z")),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("IMPACT_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("oldVersions").get(0)).remove("reason")),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("FIELDS_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("oldVersions").get(0)).put("reason", "   ")),
        PublicationFixtureSupport.bundleBytes())).hasMessageContaining("FIELD_INVALID");

    JsonNode oracle = WireCodec.JSON.readTree(Files.readAllBytes(
        Path.of("../../../contracts/fixtures/publication/fx05-impact-oracle.json")));
    for (String classification : List.of(
        "COSMETIC", "REVERIFY", "WITHDRAWN", "INVALID_OLD_RUBRIC")) {
      JsonNode policy = oracle.path(classification);
      Consumer<ObjectNode> accepted = candidate -> setPolicy(
          candidate,
          classification,
          policy.path("newAttemptAllowed").asBoolean(),
          policy.path("submitAllowed").asBoolean(),
          policy.path("finalizeAllowed").asBoolean(),
          policy.path("currentEvidencePolicy").asString(),
          policy.path("bodyAvailable").asBoolean());
      assertThat(validator.validate(
          PublicationFixtureSupport.mutateAndBind(accepted), PublicationFixtureSupport.bundleBytes())
          .target()).isEqualTo(2);

      for (String booleanField : List.of(
          "newAttemptAllowed", "submitAllowed", "finalizeAllowed", "bodyAvailable")) {
        assertThatThrownBy(() -> validator.validate(
            PublicationFixtureSupport.mutateAndBind(candidate -> {
              accepted.accept(candidate);
              ObjectNode manifest = (ObjectNode) candidate.path("oldVersions").get(0);
              manifest.put(booleanField, !manifest.path(booleanField).asBoolean());
            }), PublicationFixtureSupport.bundleBytes()))
            .hasMessageContaining("MANIFEST_POLICY_CONTRADICTION");
      }
      assertThatThrownBy(() -> validator.validate(
          PublicationFixtureSupport.mutateAndBind(candidate -> {
            accepted.accept(candidate);
            ((ObjectNode) candidate.path("oldVersions").get(0)).put(
                "currentEvidencePolicy",
                policy.path("currentEvidencePolicy").asString().equals("RETAIN") ? "REVIEW" : "RETAIN");
          }), PublicationFixtureSupport.bundleBytes()))
          .hasMessageContaining("MANIFEST_POLICY_CONTRADICTION");
    }
  }

  @Test
  void referencesDagAndPrivatePayloadBindingsFailClosed() throws Exception {
    byte[] bundle = PublicationFixtureSupport.bundleBytes();
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ArrayNode) candidate.path("references")).add(
                candidate.path("references").get(0).deepCopy())), bundle))
        .hasMessageContaining("REFERENCE_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("references").get(0)).put("expectedType", "case")), bundle))
        .hasMessageContaining("REFERENCE_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("references").get(0)).put("to", "CAP-COL-999-Z")), bundle))
        .hasMessageContaining("REFERENCE_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate ->
            ((ObjectNode) candidate.path("prerequisiteEdges").get(0))
                .put("to", "UNIT-CATALOG-FIXTURE")), bundle))
        .hasMessageContaining("DAG_INVALID");
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidate -> {
          ObjectNode reverse = ((ObjectNode) candidate.path("prerequisiteEdges").get(0)).deepCopy();
          reverse.put("from", "CAP-COL-002-A").put("to", "UNIT-CATALOG-FIXTURE");
          ((ArrayNode) candidate.path("prerequisiteEdges")).add(reverse);
        }), bundle)).hasMessageContaining("DAG_INVALID");

    ObjectNode wrongTarget = PublicationFixtureSupport.bundleNode();
    ((ArrayNode) wrongTarget.path("rubricTargets")).removeAll().add("CAP-COL-999-Z");
    byte[] wrongTargetBytes = WireCodec.JSON.writeValueAsBytes(wrongTarget);
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.candidateForBundle(wrongTargetBytes, candidate -> {}),
        wrongTargetBytes)).hasMessageContaining("PRIVATE_BUNDLE_BINDING_INVALID");

    ObjectNode extraPrivateField = PublicationFixtureSupport.bundleNode();
    extraPrivateField.put("leak", true);
    byte[] extraPrivateBytes = WireCodec.JSON.writeValueAsBytes(extraPrivateField);
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.candidateForBundle(extraPrivateBytes, candidate -> {}),
        extraPrivateBytes)).hasMessageContaining("FIELDS_INVALID");

    ObjectNode invalidExpected = PublicationFixtureSupport.bundleNode();
    invalidExpected.set("expected", WireCodec.JSON.createArrayNode().add("not-an-object"));
    byte[] invalidExpectedBytes = WireCodec.JSON.writeValueAsBytes(invalidExpected);
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.candidateForBundle(invalidExpectedBytes, candidate -> {}),
        invalidExpectedBytes)).hasMessageContaining("PRIVATE_BUNDLE_INVALID");
  }

  @Test
  void unknownDuplicateMalformedUtf8AndBoundsFailClosed() throws Exception {
    byte[] bundle = PublicationFixtureSupport.bundleBytes();
    String candidate = Files.readString(
        Path.of("../../../contracts/fixtures/publication/fx05-candidate-cosmetic.json"));
    assertThatThrownBy(() -> validator.validate(
        candidate.replaceFirst("\\{", "{\"privileged\":true,").getBytes(StandardCharsets.UTF_8), bundle))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate(
        candidate.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1,\"schemaVersion\": 1")
            .getBytes(StandardCharsets.UTF_8), bundle))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate(new byte[] {(byte) 0xc3, (byte) 0x28}, bundle))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate("{\"schemaVersion\":".getBytes(StandardCharsets.UTF_8), bundle))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate(
        PublicationFixtureSupport.mutateAndBind(candidateNode ->
            ((ObjectNode) candidateNode.path("review")).put("reportId", "review-" + "a".repeat(122))),
        bundle)).hasMessageContaining("FIELD_INVALID");
  }

  @Test
  void executableJsonSchemaMatchesStrictJavaStructuralCorpus() throws Exception {
    List<byte[]> corpus = new ArrayList<>();
    corpus.add(PublicationFixtureSupport.candidateBytes());
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ObjectNode) candidate.path("bundle")).put("extra", true)));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ObjectNode) candidate.path("components").get(0)).remove("sha256")));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ObjectNode) candidate.path("components").get(0)).put("type", "unknown")));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        candidate.put("schemaVersion", true)));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        candidate.put("operationId", candidate.path("operationId").asString().toUpperCase())));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate -> {
      candidate.put("parentRevision", 9_007_199_254_740_992L);
      candidate.put("targetRevision", 9_007_199_254_740_993L);
    }));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ObjectNode) candidate.path("coverage")).put("reportId", "coverage bad")));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ObjectNode) candidate.path("oldVersions").get(0)).put("reason", "x".repeat(513))));
    corpus.add(PublicationFixtureSupport.serializeWithoutBinding(candidate ->
        ((ArrayNode) candidate.path("impacts").get(0).path("affectedVersions")).removeAll()));

    List<Path> candidates = new ArrayList<>();
    for (int index = 0; index < corpus.size(); index++) {
      Path path = temporary.resolve("candidate-" + index + ".json");
      Files.write(path, corpus.get(index));
      candidates.add(path);
    }
    List<String> command = new ArrayList<>();
    command.add("node");
    command.add(Path.of("../../../scripts/publication/schema-parity.mjs").toRealPath().toString());
    command.add(Path.of("../../../contracts/http/content/internal/publication/candidate-v1.schema.json")
        .toRealPath().toString());
    candidates.forEach(path -> command.add(path.toString()));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).as(output).isZero();
    JsonNode schemaVerdicts = WireCodec.JSON.readTree(output);
    assertThat(schemaVerdicts.size()).isEqualTo(corpus.size());

    for (int index = 0; index < corpus.size(); index++) {
      boolean javaAccepted;
      try {
        validator.validate(corpus.get(index), PublicationFixtureSupport.bundleBytes());
        javaAccepted = true;
      } catch (IllegalArgumentException expected) {
        javaAccepted = false;
      }
      assertThat(schemaVerdicts.get(index).asBoolean())
          .as("schema/Java structural parity case " + index)
          .isEqualTo(javaAccepted);
    }
  }

  private static void setPolicy(
      ObjectNode candidate,
      String classification,
      boolean newAttempt,
      boolean submit,
      boolean finalizeAttempt,
      String currentPolicy,
      boolean bodyAvailable) {
    ((ObjectNode) candidate.path("impacts").get(0)).put("classification", classification);
    ObjectNode manifest = (ObjectNode) candidate.path("oldVersions").get(0);
    manifest.put("newAttemptAllowed", newAttempt)
        .put("submitAllowed", submit)
        .put("finalizeAllowed", finalizeAttempt)
        .put("currentEvidencePolicy", currentPolicy)
        .put("bodyAvailable", bodyAvailable);
  }
}
