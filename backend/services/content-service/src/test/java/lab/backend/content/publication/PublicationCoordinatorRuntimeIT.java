package lab.backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import lab.backend.content.transport.TransportPeerClient;
import lab.backend.content.transport.TransportSettings;
import lab.backend.content.transport.WireCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Packaged Content/Learning + own PostgreSQL/mTLS chứng minh coordinator thật. */
@Timeout(120)
class PublicationCoordinatorRuntimeIT {
  private static final byte[] CAPABILITY =
      "publication-coordinator-runtime-capability-v1".getBytes(StandardCharsets.UTF_8);
  private static final UUID OPERATION =
      UUID.fromString("20000000-0000-4000-8000-000000000001");
  private static final UUID NEXT_OPERATION =
      UUID.fromString("20000000-0000-4000-8000-000000000002");
  private static final Properties DATABASE = new Properties();
  private static final Properties CONTENT = new Properties();
  private static final Properties LEARNING = new Properties();

  private static Path directory;
  private static Path contentLog;
  private static Path learningLog;
  private static Path contentProviderConfig;
  private static Path learningConfig;
  private static Process content;
  private static Process learning;
  private static TransportSettings coordinatorSettings;

  @BeforeAll
  static void startPackagedPeers() throws Exception {
    String databaseConfig = System.getProperty("lab.config");
    String trustConfig = System.getProperty("trust.config");
    if (databaseConfig == null || trustConfig == null
        || databaseConfig.startsWith("${") || trustConfig.startsWith("${")) {
      throw new IllegalStateException("Managed R06c PostgreSQL/mTLS required");
    }
    try (Reader reader = Files.newBufferedReader(Path.of(databaseConfig).toRealPath())) {
      DATABASE.load(reader);
    }

    Path contentConfig = Path.of(trustConfig).toRealPath();
    directory = contentConfig.getParent();
    learningConfig = directory.resolve("learning.properties").toRealPath();
    load(contentConfig, CONTENT);
    load(learningConfig, LEARNING);

    // Content provider giữ topology R06a 8080/8443 cho Learning pull INT-03.
    // Coordinator dùng bản config canonical riêng để fixed route tới Learning 18443.
    contentProviderConfig = directory.resolve("content-provider.properties");
    Files.copy(contentConfig, contentProviderConfig, StandardCopyOption.REPLACE_EXISTING);
    CONTENT.setProperty("schemaVersion", "2");
    CONTENT.setProperty("listenerTopology", TransportSettings.CANONICAL);
    store(contentConfig, CONTENT);
    LEARNING.setProperty("schemaVersion", "2");
    LEARNING.setProperty("listenerTopology", "r06b-content-learning-loopback-v1");
    store(learningConfig, LEARNING);
    Files.write(directory.resolve("publication-coordinator.cap"), CAPABILITY);

    migrate(source("content", "migrator"),
        Path.of("src/main/resources/db/migration").toRealPath());
    migrate(source("learning", "migrator"),
        Path.of("../learning-service/src/main/resources/db/migration").toRealPath());
    seedEnvironment();

    StandardEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles("canonical");
    environment.getPropertySources().addFirst(new MapPropertySource(
        "coordinator-fixture",
        Map.of("backendlab.trust.config", contentConfig.toString())));
    coordinatorSettings = TransportSettings.load(environment);

    PublicationRuntime runtime = new PublicationRuntime(coordinatorSettings);
    runtime.store().prepare(candidateForSelectedCase());
    runtime.store().start(OPERATION);

    contentLog = directory.resolve("content-coordinator-r06c.log");
    learningLog = directory.resolve("learning-coordinator-r06c.log");
    content = start("content", contentProviderConfig, contentLog);
    await(content, 8443, "learning", "content");
    learning = start("learning", learningConfig, learningLog);
    await(learning, 18443, "content", "learning");
  }

  @AfterAll
  static void stopAndAudit() throws Exception {
    stop(learning);
    stop(content);
    for (int port : new int[] {8080, 8443, 18083, 18443}) {
      assertThat(closed(port)).as("owned listener closed " + port).isTrue();
    }
    for (Path log : List.of(contentLog, learningLog)) {
      if (log != null && Files.exists(log)) {
        assertThat(Files.readString(log))
            .doesNotContain(
                "PRIVATE_COORDINATOR_RUNTIME_EXPECTED",
                new String(CAPABILITY, StandardCharsets.UTF_8),
                "SQLException",
                "password");
      }
    }
    Files.deleteIfExists(directory.resolve("publication-coordinator.cap"));
    Files.deleteIfExists(contentProviderConfig);
  }

  @Test
  void packagedCoordinatorRecoversParticipantDownLeaseAndCompletesExactRelease()
      throws Exception {
    assertPackagedCoordinatorCliFailsClosed();
    assertThat(dispatch()).isTrue();
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("FENCE_PENDING");
    assertThat(text("learning", "app",
        "SELECT state FROM learning.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("STAGED");

    stop(learning);
    learning = null;
    assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
        .isInstanceOf(Exception.class);
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(1);
    assertThat(number("content", "app",
        "SELECT count(*) FROM content.publication_phase_receipt "
            + "WHERE operation_id='" + OPERATION + "' AND phase='FENCE'"))
        .isZero();

    learning = start("learning", learningConfig, learningLog);
    await(learning, 18443, "content", "learning");
    // Fault barrier giữ lease live để process mới không gửi song song cùng prefix.
    execute("content", "migrator",
        "UPDATE content.publication_phase_outbox "
            + "SET lease_until=clock_timestamp()+interval '10 seconds' "
            + "WHERE operation_id='" + OPERATION + "' AND phase='FENCE'");
    PublicationStore store = new PublicationRuntime(coordinatorSettings).store();
    assertThatThrownBy(() -> store.lease(OPERATION))
        .isInstanceOf(java.sql.SQLException.class)
        .hasMessageContaining("PUBLICATION_DELIVERY_LEASE_CONFLICT");
    assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
        .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
    execute("content", "migrator",
        "UPDATE content.publication_phase_outbox "
            + "SET lease_until=clock_timestamp()-interval '1 second' "
            + "WHERE operation_id='" + OPERATION + "' AND phase='FENCE'");

    assertThat(dispatch()).isTrue();
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(2);
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("RELEASE_PENDING");
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isFalse();

    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("COMPLETE");
    assertThat(number("content", "app",
        "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
        .isEqualTo(2);
    assertThat(number("content", "app",
        "SELECT count(*) FROM content.publication_phase_receipt WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo(3);
    assertThat(text("learning", "app",
        "SELECT state FROM learning.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("RELEASED");
    assertThat(number("learning", "app",
        "SELECT applied_revision FROM learning.publication_head WHERE singleton"))
        .isEqualTo(2);
    assertThat(number("learning", "app",
        "SELECT last_sequence FROM learning.publication_delivery_cursor "
            + "WHERE stream_key='content-global'"))
        .isEqualTo(3);
  }

  @Test
  void data02GateBeforeCommitKeepsLearningPendingUntilExactRelease() throws Exception {
    assertThat(dispatch()).isTrue();
    assertThat(text("learning", "app",
        "SELECT state FROM learning.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("STAGED");
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(1);

    assertThat(dispatch()).isTrue();
    assertThat(text("learning", "app",
        "SELECT state FROM learning.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("FENCED");
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("RELEASE_PENDING");
    assertThat(number("content", "app",
        "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
        .isEqualTo(1);

    assertThat(dispatch()).isTrue();
    assertComplete(OPERATION, 2);
  }

  @Test
  void data03ParticipantDownBeforeFenceAckKeepsCanonicalAndRecovers() throws Exception {
    assertThat(dispatch()).isTrue();
    stop(learning);
    learning = null;
    assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
        .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(1);
    assertThat(number("content", "app",
        "SELECT count(*) FROM content.publication_phase_receipt "
            + "WHERE operation_id='" + OPERATION + "' AND phase='FENCE'"))
        .isZero();

    learning = start("learning", learningConfig, learningLog);
    await(learning, 18443, "content", "learning");
    expireLease(OPERATION, "FENCE");
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isTrue();
    assertComplete(OPERATION, 2);
  }

  @Test
  void data04CanonicalAheadPendingSurvivesRestartAndCompletesRelease() throws Exception {
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isTrue();
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(2);
    assertThat(number("content", "app",
        "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
        .isEqualTo(1);

    stop(learning);
    learning = null;
    assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
        .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("RELEASE_PENDING");
    assertThat(number("content", "app",
        "SELECT count(*) FROM content.publication_phase_outbox "
            + "WHERE operation_id='" + OPERATION + "' AND phase='RELEASE'"))
        .isOne();

    learning = start("learning", learningConfig, learningLog);
    await(learning, 18443, "content", "learning");
    expireLease(OPERATION, "RELEASE");
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isFalse();
    assertComplete(OPERATION, 2);
  }

  @Test
  void data05AbortAndNewOperationUseExactGlobalPrefix() throws Exception {
    abort(OPERATION);
    assertThat(dispatch()).isTrue();
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("ABORT_PENDING");
    assertThat(dispatch()).isTrue();
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(1);
    assertThat(dispatch()).isFalse();

    PublicationStore store = new PublicationRuntime(coordinatorSettings).store();
    store.prepare(PublicationFixtureSupport.variant(
        NEXT_OPERATION,
        "fx05-coordinator-runtime",
        "PRIVATE_COORDINATOR_RUNTIME_EXPECTED",
        "Coordinator runtime cosmetic change"));
    start(NEXT_OPERATION);
    assertThat(number("content", "app",
        "SELECT delivery_sequence FROM content.publication_phase_outbox "
            + "WHERE operation_id='" + NEXT_OPERATION + "' AND phase='STAGE'"))
        .isEqualTo(3);
    assertThat(dispatch(NEXT_OPERATION)).isTrue();
    assertThat(dispatch(NEXT_OPERATION)).isTrue();
    assertThatThrownBy(() -> abort(NEXT_OPERATION))
        .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
    assertThat(dispatch(NEXT_OPERATION)).isTrue();
    assertComplete(NEXT_OPERATION, 2);
  }

  @Test
  void data06WithdrawalManifestRemainsExactThroughCoordinator() throws Exception {
    assertPolicyAndComplete("WITHDRAWN", "WITHDRAW");
  }

  @Test
  void data07InvalidRubricManifestRemainsExactThroughCoordinator() throws Exception {
    assertPolicyAndComplete("INVALID_OLD_RUBRIC", "REVIEW");
  }

  @Test
  void data14AuthenticatedWrongReceiptBindingsHaveNoEffect() throws Exception {
    PublicationRuntime runtime = new PublicationRuntime(coordinatorSettings);
    PublicationStore store = runtime.store();
    PublicationStore.LeasedPending leased = store.lease(OPERATION);
    byte[] receipt;
    try (PublicationPeerClient peer = new PublicationPeerClient(coordinatorSettings)) {
      receipt = peer.send(
          new PublicationPeerClient.UUIDAndPhase(OPERATION, "STAGE"),
          leased.pending().commandBytes());
    }

    for (String field : List.of(
        "realmId", "participantGeneration", "bundleSha256", "deliverySequence")) {
      ObjectNode wrong = (ObjectNode) WireCodec.JSON.readTree(receipt);
      if (field.equals("deliverySequence")) {
        wrong.put(field, leased.pending().sequence() + 1);
      } else if (field.equals("bundleSha256")) {
        wrong.put(field, "f".repeat(64));
      } else {
        wrong.put(field, UUID.randomUUID().toString());
      }
      byte[] wrongBytes = WireCodec.JSON.writeValueAsBytes(wrong);
      assertThatThrownBy(() -> store.accept(leased, wrongBytes))
          .isInstanceOf(java.sql.SQLException.class)
          .hasRootCauseMessage("PUBLICATION_RESPONSE_BINDING_INVALID");
      assertThat(text("content", "app",
          "SELECT state FROM content.publication_operation WHERE operation_id='"
              + OPERATION + "'"))
          .isEqualTo("STAGE_PENDING");
    }

    store.accept(leased, receipt);
    stop(learning);
    learning = null;
    AtomicReference<String> mode = new AtomicReference<>("NOT_FOUND");
    try (FaultPeer peer = faultPeer("learning", mode)) {
      for (String fault : List.of(
          "NOT_FOUND", "UNAVAILABLE", "MALFORMED", "OVERSIZE", "TRUNCATED", "SLOW")) {
        mode.set(fault);
        expireLease(OPERATION, "FENCE");
        assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
            .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
        assertPendingFenceHasNoCanonicalEffect();
      }
    }
    try (FaultPeer ignored = faultPeer("content", new AtomicReference<>("MALFORMED"))) {
      expireLease(OPERATION, "FENCE");
      assertThatThrownBy(PublicationCoordinatorRuntimeIT::dispatchWithoutRecovery)
          .hasMessageContaining("PUBLICATION_CANDIDATE_REJECTED");
      assertPendingFenceHasNoCanonicalEffect();
    }

    learning = start("learning", learningConfig, learningLog);
    await(learning, 18443, "content", "learning");
    expireLease(OPERATION, "FENCE");
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isTrue();
    assertComplete(OPERATION, 2);
  }

  private static boolean dispatch() throws Exception {
    return dispatch(OPERATION);
  }

  private static boolean dispatch(UUID operationId) throws Exception {
    try {
      return dispatchWithoutRecovery(operationId);
    } catch (IllegalStateException uncertainOutcome) {
      if (!uncertainOutcome.getMessage().contains("PUBLICATION_CANDIDATE_REJECTED")) {
        throw uncertainOutcome;
      }
      // CLI cố ý redacted nên caller không được đoán request đã tới peer hay chưa.
      // Operator retry đúng protocol phải chờ/expire durable lease rồi gửi lại
      // exact bytes; chỉ cho một retry để repeated fault vẫn làm test đỏ.
      PublicationStore.Pending pending =
          new PublicationRuntime(coordinatorSettings).store().pending(operationId);
      if (pending != null) {
        expireLease(operationId, pending.phase());
      }
      return dispatchWithoutRecovery(operationId);
    }
  }

  private static boolean dispatchWithoutRecovery() throws Exception {
    return dispatchWithoutRecovery(OPERATION);
  }

  private static boolean dispatchWithoutRecovery(UUID operationId) throws Exception {
    String output = coordinatorCli("dispatch", operationId);
    return output.contains(" sent=true");
  }

  private static void start(UUID operationId) throws Exception {
    assertThat(coordinatorCli("start", operationId))
        .contains("PUBLICATION_STARTED operation=" + operationId);
  }

  private static void abort(UUID operationId) throws Exception {
    assertThat(coordinatorCli("abort", operationId))
        .contains("PUBLICATION_ABORT_PENDING operation=" + operationId);
  }

  private static String coordinatorCli(String action, UUID operationId) throws Exception {
    CliResult result = runCoordinatorCli(
        directory.resolve("content.properties"),
        action,
        operationId.toString());
    if (result.exitCode() != 0) {
      throw new IllegalStateException(result.stderr().trim());
    }
    return result.stdout().trim();
  }

  private static void assertPackagedCoordinatorCliFailsClosed() throws Exception {
    Path validConfig = directory.resolve("content.properties");
    List<String[]> invalidArguments = List.of(
        new String[] {"unknown", OPERATION.toString()},
        new String[] {"status", "not-a-uuid"},
        new String[] {"start", OPERATION.toString(), "extra"},
        new String[] {"dispatch"});
    for (String[] arguments : invalidArguments) {
      assertRejectedCli(runCoordinatorCli(validConfig, arguments));
    }
    assertRejectedCli(runCoordinatorCli(
        directory.resolve("missing-content.properties"),
        "status",
        OPERATION.toString()));
  }

  private static void assertRejectedCli(CliResult result) {
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isBlank();
    List<String> applicationLines = result.stderr().lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        // Managed runner injects this JVM note before main(); it is not CLI output.
        .filter(line -> !line.startsWith("NOTE: Picked up JDK_JAVA_OPTIONS:"))
        .toList();
    assertThat(applicationLines).containsExactly("PUBLICATION_CANDIDATE_REJECTED");
  }

  private static CliResult runCoordinatorCli(Path config, String... arguments) throws Exception {
    Path artifact = Path.of(
        "../content-service/target/content-service-0.1.0-SNAPSHOT.jar").toRealPath();
    List<String> command = new java.util.ArrayList<>(List.of(
        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
        "-Dspring.profiles.active=canonical",
        "-Dloader.main=lab.backend.content.publication.PublicationLabCli",
        "-Dbackendlab.trust.config=" + config,
        "-cp",
        artifact.toString(),
        "org.springframework.boot.loader.launch.PropertiesLauncher"));
    command.addAll(java.util.Arrays.asList(arguments));
    Process process = new ProcessBuilder(command)
        .directory(directory.toFile())
        .start();
    assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(stdout + stderr).doesNotContain(
        "PRIVATE_COORDINATOR_RUNTIME_EXPECTED",
        new String(CAPABILITY, StandardCharsets.UTF_8),
        "SQLException",
        "password");
    return new CliResult(process.exitValue(), stdout, stderr);
  }

  private record CliResult(int exitCode, String stdout, String stderr) {}

  private static CandidateValidator.Valid candidateForSelectedCase() throws Exception {
    String selectedCase = System.getProperty("r06c.case", "");
    if (selectedCase.equals("DATA-06")) {
      return policyCandidate("WITHDRAWN", "WITHDRAW");
    }
    if (selectedCase.equals("DATA-07")) {
      return policyCandidate("INVALID_OLD_RUBRIC", "REVIEW");
    }
    return PublicationFixtureSupport.variant(
        OPERATION,
        "fx05-coordinator-runtime",
        "PRIVATE_COORDINATOR_RUNTIME_EXPECTED",
        "Coordinator runtime cosmetic change");
  }

  private static CandidateValidator.Valid policyCandidate(
      String classification,
      String currentPolicy) throws Exception {
    ObjectNode bundle = PublicationFixtureSupport.bundleNode();
    ((ObjectNode) bundle.path("expected"))
        .put("sentinel", "PRIVATE_COORDINATOR_RUNTIME_EXPECTED");
    byte[] bundleBytes = WireCodec.JSON.writeValueAsBytes(bundle);
    JsonNode oracle = WireCodec.JSON.readTree(Files.readAllBytes(
        Path.of("../../../contracts/fixtures/publication/fx05-impact-oracle.json")));
    JsonNode policy = oracle.path(classification);
    byte[] candidateBytes = PublicationFixtureSupport.candidateForBundle(
        bundleBytes,
        candidate -> {
          candidate.put("operationId", OPERATION.toString());
          ((ObjectNode) candidate.path("bundle"))
              .put("id", "fx05-coordinator-" + classification.toLowerCase())
              .put("sha256", PublicationDigest.sha256(bundleBytes));
          for (JsonNode component : candidate.path("components")) {
            if (component.path("type").asString().equals("case")) {
              ((ObjectNode) component).put("sha256", PublicationDigest.sha256(bundleBytes));
            }
          }
          ((ObjectNode) candidate.path("impacts").get(0))
              .put("classification", classification);
          ObjectNode manifest = (ObjectNode) candidate.path("oldVersions").get(0);
          manifest.put("newAttemptAllowed", policy.path("newAttemptAllowed").asBoolean())
              .put("submitAllowed", policy.path("submitAllowed").asBoolean())
              .put("finalizeAllowed", policy.path("finalizeAllowed").asBoolean())
              .put("currentEvidencePolicy", currentPolicy)
              .put("bodyAvailable", policy.path("bodyAvailable").asBoolean())
              .put("reason", "Coordinator exact policy binding");
        });
    return new CandidateValidator().validate(candidateBytes, bundleBytes);
  }

  private static void assertPolicyAndComplete(
      String classification,
      String currentPolicy) throws Exception {
    String storedManifest = new String(bytes(
        "content",
        "app",
        "SELECT manifest_bytes FROM content.publication_bundle "
            + "WHERE bundle_id=(SELECT bundle_id FROM content.publication_operation "
            + "WHERE operation_id='" + OPERATION + "')"), StandardCharsets.UTF_8);
    String storedCandidate = new String(bytes(
        "content",
        "app",
        "SELECT candidate_bytes FROM content.publication_candidate "
            + "WHERE operation_id='" + OPERATION + "'"), StandardCharsets.UTF_8);
    assertThat(storedCandidate)
        .contains("\"classification\":\"" + classification + "\"");
    assertThat(storedManifest)
        .contains("\"currentEvidencePolicy\":\"" + currentPolicy + "\"");
    String manifestDigest = PublicationDigest.sha256(
        storedManifest.getBytes(StandardCharsets.UTF_8));
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isTrue();
    assertThat(dispatch()).isTrue();
    assertThat(text("learning", "app",
        "SELECT manifest_sha256 FROM learning.publication_operation "
            + "WHERE operation_id='" + OPERATION + "'"))
        .isEqualTo(manifestDigest);
    assertThat(PublicationDigest.sha256(bytes(
        "learning",
        "app",
        "SELECT manifest_bytes FROM learning.content_replica "
            + "WHERE bundle_id=(SELECT bundle_id FROM learning.publication_operation "
            + "WHERE operation_id='" + OPERATION + "')")))
        .isEqualTo(manifestDigest);
    assertComplete(OPERATION, 2);
  }

  private static void assertComplete(UUID operationId, long revision) throws Exception {
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + operationId + "'"))
        .isEqualTo("COMPLETE");
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(revision);
    assertThat(number("content", "app",
        "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
        .isEqualTo(revision);
    assertThat(text("learning", "app",
        "SELECT state FROM learning.publication_operation WHERE operation_id='"
            + operationId + "'"))
        .isEqualTo("RELEASED");
  }

  private static void expireLease(UUID operationId, String phase) throws Exception {
    execute("content", "migrator",
        "UPDATE content.publication_phase_outbox "
            + "SET lease_until=clock_timestamp()-interval '1 second' "
            + "WHERE operation_id='" + operationId + "' AND phase='" + phase + "'");
  }

  private static void assertPendingFenceHasNoCanonicalEffect() throws Exception {
    assertThat(text("content", "app",
        "SELECT state FROM content.publication_operation WHERE operation_id='"
            + OPERATION + "'"))
        .isEqualTo("FENCE_PENDING");
    assertThat(number("content", "app",
        "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo(1);
    assertThat(number("content", "app",
        "SELECT count(*) FROM content.publication_phase_receipt "
            + "WHERE operation_id='" + OPERATION + "' AND phase='FENCE'"))
        .isZero();
  }

  private static FaultPeer faultPeer(
      String certificate,
      AtomicReference<String> mode) throws Exception {
    SSLContext ssl = TransportPeerClient.tls(
        coordinatorSettings,
        directory.resolve(certificate + ".p12"),
        "content");
    HttpsServer server = HttpsServer.create(
        new InetSocketAddress("127.0.0.1", 18443),
        0);
    server.setHttpsConfigurator(new HttpsConfigurator(ssl) {
      @Override
      public void configure(HttpsParameters parameters) {
        SSLParameters configured = ssl.getDefaultSSLParameters();
        configured.setNeedClientAuth(true);
        configured.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        parameters.setSSLParameters(configured);
      }
    });
    server.createContext("/", exchange -> {
      try {
        exchange.getRequestBody().readAllBytes();
        String selected = mode.get();
        if (selected.equals("NOT_FOUND")) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        if (selected.equals("UNAVAILABLE")) {
          exchange.sendResponseHeaders(503, -1);
          return;
        }
        if (selected.equals("SLOW")) {
          Thread.sleep(5_500);
        }
        byte[] body = switch (selected) {
          case "OVERSIZE" -> new byte[PublicationCoordinatorWire.MAX_BYTES + 1];
          case "MALFORMED", "SLOW" -> "{".getBytes(StandardCharsets.UTF_8);
          default -> "{}".getBytes(StandardCharsets.UTF_8);
        };
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        long declaredLength = selected.equals("TRUNCATED") ? body.length + 7L : body.length;
        exchange.sendResponseHeaders(200, declaredLength);
        exchange.getResponseBody().write(body);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    server.setExecutor(executor);
    server.start();
    return new FaultPeer(server, executor);
  }

  private record FaultPeer(HttpsServer server, ExecutorService executor)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      server.stop(0);
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      assertThat(closed(18443)).isTrue();
    }
  }

  private static void seedEnvironment() throws Exception {
    execute("content", "migrator",
        "INSERT INTO content.publication_canonical_head(revision,bundle_id,bundle_digest) "
            + "VALUES(1,'fx05-p1',repeat('0',64))");
    try (Connection connection = source("content", "migrator").getConnection();
        var statement = connection.prepareStatement(
            "INSERT INTO content.publication_environment("
                + "singleton,realm_id,producer_generation,participant_generation,"
                + "coordinator_capability_sha256,learning_ready_revision) "
                + "VALUES(true,?,?,?,sha256(?),1)")) {
      statement.setObject(1, UUID.fromString(LEARNING.getProperty("realm")));
      statement.setObject(2, UUID.fromString(CONTENT.getProperty("generation")));
      statement.setObject(3, UUID.fromString(LEARNING.getProperty("generation")));
      statement.setBytes(4, CAPABILITY);
      statement.executeUpdate();
    }
    execute("learning", "migrator",
        "INSERT INTO learning.publication_environment VALUES(true,'"
            + LEARNING.getProperty("realm") + "','"
            + LEARNING.getProperty("generation") + "','"
            + CONTENT.getProperty("generation") + "',true)");
    execute("learning", "migrator",
        "INSERT INTO learning.publication_head(singleton,applied_revision) VALUES(true,1)");
    execute("learning", "migrator",
        "INSERT INTO learning.publication_delivery_cursor(stream_key,last_sequence) "
            + "VALUES('content-global',0)");
  }

  private static void migrate(PGSimpleDataSource dataSource, Path path) {
    Flyway.configure()
        .dataSource(dataSource)
        .defaultSchema("migration_metadata")
        .schemas("migration_metadata")
        .createSchemas(false)
        .cleanDisabled(true)
        .locations("filesystem:" + path.toString().replace('\\', '/'))
        .load()
        .migrate();
  }

  private static PGSimpleDataSource source(String service, String kind) {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {"127.0.0.1"});
    dataSource.setPortNumbers(new int[] {
        Integer.parseInt(DATABASE.getProperty("lab.port"))
    });
    dataSource.setDatabaseName(service + "_db");
    dataSource.setUser(DATABASE.getProperty(service + "." + kind + ".user"));
    dataSource.setPassword(DATABASE.getProperty(service + "." + kind + ".password"));
    dataSource.setSslMode("disable");
    return dataSource;
  }

  private static Process start(String service, Path config, Path log) throws Exception {
    Path jar = Path.of(
        "../" + service + "-service/target/"
            + service + "-service-0.1.0-SNAPSHOT.jar").toRealPath();
    ProcessBuilder builder = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
        "-jar",
        jar.toString(),
        "--spring.profiles.active=publication",
        "--backendlab.trust.config=" + config,
        "--logging.level.root=ERROR");
    builder.directory(directory.toFile());
    builder.redirectErrorStream(true);
    builder.redirectOutput(log.toFile());
    builder.environment().keySet().removeIf(key ->
        key.startsWith("SPRING_") || key.startsWith("SERVER_")
            || key.startsWith("MANAGEMENT_") || key.equals("JAVA_TOOL_OPTIONS")
            || key.equals("_JAVA_OPTIONS"));
    return builder.start();
  }

  private static void await(
      Process process,
      int port,
      String caller,
      String service) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(service + " packaged process exited");
      }
      try {
        if (call(caller, port).statusCode() > 0) {
          return;
        }
      } catch (Exception ignored) {
        // Readiness chỉ thành công khi TLS probe thật trả response.
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException(service + " packaged process not ready");
  }

  private static HttpResponse<byte[]> call(String caller, int port) throws Exception {
    SSLContext ssl = TransportPeerClient.tls(
        coordinatorSettings,
        directory.resolve(caller + ".p12"),
        port == 8443 ? "content" : "learning");
    try (HttpClient client = HttpClient.newBuilder()
        .sslContext(ssl)
        .connectTimeout(Duration.ofSeconds(2))
        .build()) {
      HttpRequest request = HttpRequest.newBuilder(
          URI.create("https://127.0.0.1:" + port + "/internal/v1/transport/probes"))
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
      return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  private static void execute(String service, String kind, String sql) throws Exception {
    try (Connection connection = source(service, kind).getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static long number(String service, String kind, String sql) throws Exception {
    try (Connection connection = source(service, kind).getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String text(String service, String kind, String sql) throws Exception {
    try (Connection connection = source(service, kind).getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }

  private static byte[] bytes(String service, String kind, String sql) throws Exception {
    try (Connection connection = source(service, kind).getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getBytes(1);
    }
  }

  private static void load(Path path, Properties properties) throws Exception {
    try (Reader reader = Files.newBufferedReader(path)) {
      properties.load(reader);
    }
  }

  private static void store(Path path, Properties properties) throws Exception {
    try (var writer = Files.newBufferedWriter(path)) {
      properties.store(writer, null);
    }
  }

  private static void stop(Process process) throws Exception {
    if (process == null) {
      return;
    }
    process.destroy();
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static boolean closed(int port) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
      } catch (java.io.IOException expected) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }
}
