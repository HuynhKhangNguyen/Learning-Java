package lab.backend.content.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import lab.backend.content.publication.CandidateValidator;
import lab.backend.content.publication.PublicationFixtureSupport;
import lab.backend.content.publication.PublicationCoordinatorWire;
import lab.backend.content.publication.PublicationStore;
import lab.backend.content.transport.WireCodec;
import tools.jackson.databind.node.ObjectNode;

/** Real DB fixtures only; no learner/auth/work algorithm or simulated PostgreSQL. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(30)
class DatabaseIsolationIT {
  private static final String SERVICE = "content";
  private static final byte[] PUBLICATION_CAPABILITY = "publication-coordinator-test-capability-v1"
      .getBytes(StandardCharsets.UTF_8);
  private static final Properties CONFIG = new Properties();
  private static int initialMigrations;

  @BeforeAll
  static void requireOwnedFreshLabAndMigrate() throws Exception {
    String input = System.getProperty("lab.config");
    if (input == null || input.isBlank() || input.startsWith("${")) {
      throw new IllegalStateException("CMD-03 needs a managed disposable lab; missing config is not a skipped test");
    }
    Path config = Path.of(input).toRealPath();
    Path directory = config.getParent();
    assertThat(directory.getParent().getFileName().toString()).isEqualTo("db-lab");
    assertThat(directory.getParent().getParent().getFileName().toString()).isEqualTo(".tools");
    assertThat(Files.isRegularFile(directory.resolve("ownership.json"))).isTrue();
    try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { CONFIG.load(reader); }
    assertThat(CONFIG.getProperty("lab.host")).isEqualTo("127.0.0.1");
    assertThat(Path.of(CONFIG.getProperty("lab.directory")).toRealPath()).isEqualTo(directory);
    UUID.fromString(CONFIG.getProperty("lab.runId"));
    try (Connection connection = connect("migrator", SERVICE + "_db")) {
      assertThat(text(connection, "SELECT current_setting('backend_lab.run_id')"))
          .isEqualTo(CONFIG.getProperty("lab.runId"));
      assertThat(number(connection, "SELECT count(*) FROM pg_tables WHERE schemaname IN ('" + SERVICE + "','migration_metadata')"))
          .isZero();
    }
    initialMigrations = migrations().migrate().migrationsExecuted;
  }

  @Test
  void aaVersionDatabaseAndFreshMigrationsAreReal() throws Exception {
    assertThat(initialMigrations).isEqualTo(7); // six production migrations plus test-only fixture
    try (Connection connection = connect("app", SERVICE + "_db")) {
      assertThat(text(connection, "SHOW server_version")).isEqualTo("18.6");
      assertThat(text(connection, "SELECT current_database()")).isEqualTo(SERVICE + "_db");
      assertThat(text(connection, "SELECT service_key FROM " + SERVICE + ".service_descriptor")).isEqualTo(SERVICE);
      assertThat(number(connection, "SELECT schema_revision FROM " + SERVICE + ".service_descriptor")).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"app", "migrator"})
  void authenticatedRolesHaveNoClusterPrivilege(String kind) throws Exception {
    try (Connection connection = connect(kind, SERVICE + "_db")) {
      assertThat(text(connection, "SELECT current_user")).isEqualTo(SERVICE + "_" + kind);
      assertThat(number(connection, "SELECT count(*) FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)"))
          .isZero();
      assertThat(number(connection, "SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user)"))
          .isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE TABLE content.forbidden(id integer)", "DROP TABLE content.service_descriptor", "CREATE EXTENSION dblink"})
  void appCannotPerformDdlOrLoadExtensions(String sql) throws Exception {
    try (Connection connection = connect("app", SERVICE + "_db")) { denied(connection, sql); }
  }

  @Test
  void appCannotMigrateOrModifyTheDescriptor() throws Exception {
    assertThatThrownBy(() -> flyway("app", "classpath:db/migration", "classpath:db-test-migration").migrate())
        .isInstanceOf(FlywayException.class);
    try (Connection connection = connect("app", SERVICE + "_db")) {
      denied(connection, "UPDATE " + SERVICE + ".service_descriptor SET schema_revision=2");
      assertThat(number(connection, "SELECT schema_revision FROM " + SERVICE + ".service_descriptor")).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SELECT * FROM migration_metadata.flyway_schema_history", "DELETE FROM migration_metadata.flyway_schema_history"})
  void appCannotReadOrChangeMigrationHistory(String sql) throws Exception {
    try (Connection connection = connect("app", SERVICE + "_db")) { denied(connection, sql); }
  }

  @Test
  void appHasDmlOnlyOnItsOwnFixtureTables() throws Exception {
    String id = UUID.randomUUID().toString();
    try (Connection connection = connect("app", SERVICE + "_db")) {
      assertThat(execute(connection, "INSERT INTO " + SERVICE + ".transaction_probe VALUES ('" + id + "',0,'initial')")).isEqualTo(1);
      assertThat(execute(connection, "UPDATE " + SERVICE + ".transaction_probe SET payload='updated' WHERE id='" + id + "'")).isEqualTo(1);
      assertThat(text(connection, "SELECT payload FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo("updated");
      assertThat(execute(connection, "DELETE FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo(1);
    }
  }

  @Test
  void checksumMismatchFailsValidationWithoutChangingGoodData(@TempDir Path temporary) throws Exception {
    String original;
    try (var stream = DatabaseIsolationIT.class.getResourceAsStream("/db/migration/V1__service_descriptor.sql")) {
      original = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    Files.writeString(temporary.resolve("V1__service_descriptor.sql"), original + "\nSELECT 1;\n");
    assertThatThrownBy(() -> flyway("migrator", "filesystem:" + temporary, "classpath:db-test-migration").validate())
        .isInstanceOf(FlywayException.class).hasMessageContaining("checksum mismatch");
    migrations().validate();
    try (Connection connection = connect("app", SERVICE + "_db")) {
      assertThat(text(connection, "SELECT service_key FROM " + SERVICE + ".service_descriptor")).isEqualTo(SERVICE);
    }
  }

  @Test
  void destructiveCleanIsDisabled() {
    assertThatThrownBy(() -> migrations().clean()).isInstanceOf(FlywayException.class);
  }

  static Stream<Arguments> otherDatabases() {
    return Stream.of("app", "migrator").flatMap(kind ->
        Stream.of("identity_db", "content_db", "learning_db", "postgres", "template1")
            .filter(db -> !db.equals(SERVICE + "_db")).map(db -> Arguments.of(kind, db)));
  }

  @ParameterizedTest
  @MethodSource("otherDatabases")
  void noRoleCanConnectToAnotherDatabase(String kind, String database) {
    SQLException error = assertThrows(SQLException.class, () -> {
      try (Connection ignored = connect(kind, database)) { throw new AssertionError("Cross-DB connection succeeded"); }
    });
    assertThat(error.getSQLState()).isEqualTo("42501");
  }

  @ParameterizedTest
  @ValueSource(strings = {"app", "migrator"})
  void noRoleCanEscalateToMigrationOwnerOrAdministrator(String kind) throws Exception {
    try (Connection connection = connect(kind, SERVICE + "_db")) {
      denied(connection, "SET ROLE " + (kind.equals("app") ? SERVICE + "_migrator" : "lab_admin"));
      denied(connection, "SET ROLE " + SERVICE + "_owner");
      denied(connection, "CREATE DATABASE forbidden");
      denied(connection, "CREATE ROLE forbidden LOGIN");
    }
  }

  @Test
  void reapplyingMigrationsIsIdempotent() {
    assertThat(migrations().migrate().migrationsExecuted).isZero();
    migrations().validate();
  }

  @Test
  void publicationAllocationCaseIdentityUsesExactBytesAcrossBundleIds() throws Exception {
    seedPublicationHead();
    releasePublicationReservation();
    byte[] authored = Files.readAllBytes(Path.of(
        "../../../contracts/fixtures/exposure/r08-private-a-v3.json"));
    byte[] compact = WireCodec.JSON.writeValueAsBytes(WireCodec.JSON.readTree(authored));
    assertThat(compact).isNotEqualTo(authored);

    PublicationStore store = new PublicationStore(appDataSource());
    CandidateValidator.Valid first = allocationCandidate(
        authored, UUID.randomUUID(), "r08-raw-identity-a");
    store.prepare(first);
    releasePublicationReservation();

    // Publication kế tiếp có bundle ID mới vẫn hợp lệ khi case bytes giống hệt.
    CandidateValidator.Valid exact = allocationCandidate(
        authored, UUID.randomUUID(), "r08-raw-identity-exact");
    store.prepare(exact);
    releasePublicationReservation();

    CandidateValidator.Valid semanticOnly = allocationCandidate(
        compact, UUID.randomUUID(), "r08-raw-identity-semantic-only");
    assertThatThrownBy(() -> store.prepare(semanticOnly))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PREPARE_FAILED");
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_bundle "
              + "WHERE bundle_id IN ('r08-raw-identity-a','r08-raw-identity-exact')"))
          .isEqualTo(2);
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_bundle "
              + "WHERE bundle_id='r08-raw-identity-semantic-only'"))
          .isZero();
      assertGoodPublicationHead(app);
    }
  }

  @Test
  void publicationDbFaultRollsBackEveryWriteAndKeepsGoodHead() throws Exception {
    seedPublicationHead();
    releasePublicationReservation();
    var valid = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-db-fault", "fault-private", "Injected DB fault candidate");
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator, "CREATE FUNCTION content.inject_publication_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'PUBLICATION_INJECTED_FAULT' USING ERRCODE='40001'; END $$");
      execute(migrator, "CREATE TRIGGER publication_injected_fault BEFORE INSERT ON content.publication_candidate FOR EACH ROW EXECUTE FUNCTION content.inject_publication_fault()");
    }
    try {
      assertThatThrownBy(() -> new PublicationStore(appDataSource()).prepare(valid))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("PUBLICATION_INJECTED_FAULT");
    } finally {
      try (Connection migrator = connect("migrator", SERVICE + "_db")) {
        execute(migrator, "DROP TRIGGER publication_injected_fault ON content.publication_candidate");
        execute(migrator, "DROP FUNCTION content.inject_publication_fault()");
      }
    }
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app, "SELECT count(*) FROM content.publication_bundle WHERE bundle_id='fx05-db-fault'"))
          .isZero();
      assertThat(number(app, "SELECT count(*) FROM content.publication_candidate WHERE operation_id='" + valid.operationId() + "'"))
          .isZero();
      assertThat(text(app, "SELECT operation_id::text FROM content.publication_candidate_reservation WHERE singleton"))
          .isNull();
      assertGoodPublicationHead(app);
    }
  }

  @Test
  void publicationDifferentOperationsSerializeAndReplacementAfterReleasedReservationCanPrepare()
      throws Exception {
    seedPublicationHead();
    releasePublicationReservation();
    var first = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-race-a", "race-a", "Race candidate A");
    var second = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-race-b", "race-b", "Race candidate B");
    PublicationStore store = new PublicationStore(appDataSource());
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var a = executor.submit(() -> prepareResult(store, first, start));
      var b = executor.submit(() -> prepareResult(store, second, start));
      start.countDown();
      List<String> results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
      assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
      assertThat(results.stream().filter(value -> value.contains("PUBLICATION_ACTIVE_CONFLICT")).count())
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    CandidateValidator.Valid winner;
    try (Connection app = connect("app", SERVICE + "_db")) {
      String active = text(app,
          "SELECT operation_id::text FROM content.publication_candidate_reservation WHERE singleton");
      winner = active.equals(first.operationId().toString()) ? first : second;
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_candidate WHERE operation_id IN ('"
              + first.operationId() + "','" + second.operationId() + "')"))
          .isEqualTo(1);
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_bundle WHERE bundle_id IN ('"
              + first.bundleId() + "','" + second.bundleId() + "')"))
          .isEqualTo(1);
    }

    // Trusted coordinator tương lai clear slot bằng cách tăng generation. Row
    // candidate cũ vẫn bất biến, và stale retry không được tái chiếm slot.
    releasePublicationReservation();
    assertThatThrownBy(() -> store.prepare(winner))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PUBLICATION_STALE_OPERATION");
    String expected = winner == first ? "race-a" : "race-b";
    String reason = winner == first ? "Race candidate A" : "Race candidate B";
    var replacement = PublicationFixtureSupport.variant(
        UUID.randomUUID(), winner.bundleId(), expected, reason);
    store.prepare(replacement);
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_candidate WHERE operation_id IN ('"
              + first.operationId() + "','" + second.operationId() + "','"
              + replacement.operationId() + "')"))
          .isEqualTo(2);
      assertThat(number(app, "SELECT count(*) FROM content.publication_bundle WHERE bundle_id='" + winner.bundleId() + "'"))
          .isEqualTo(1);
      assertGoodPublicationHead(app);
    }
  }

  @Test
  void publicationExactRetryDetectsCandidateAndManifestConflictAndKeepsGoodHead() throws Exception {
    seedPublicationHead();
    releasePublicationReservation();
    var valid = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-exact", "exact-private", "Exact retry candidate");
    PublicationStore store = new PublicationStore(appDataSource());
    store.prepare(valid);
    store.prepare(valid);

    byte[] changedCandidate = valid.candidateBytes();
    changedCandidate[changedCandidate.length - 2] = (byte) ' ';
    var candidateConflict = new CandidateValidator.Valid(
        valid.operationId(), valid.parent(), valid.target(), valid.bundleId(), valid.bundleDigest(),
        changedCandidate, valid.bundleBytes(), valid.manifestBytes());
    assertThatThrownBy(() -> store.prepare(candidateConflict))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PUBLICATION_CANDIDATE_CONFLICT");

    byte[] changedManifest = valid.manifestBytes();
    changedManifest[changedManifest.length - 2] = (byte) ' ';
    var manifestConflict = new CandidateValidator.Valid(
        valid.operationId(), valid.parent(), valid.target(), valid.bundleId(), valid.bundleDigest(),
        valid.candidateBytes(), valid.bundleBytes(), changedManifest);
    assertThatThrownBy(() -> store.prepare(manifestConflict))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PUBLICATION_BUNDLE_CONFLICT");

    var staleParent = new CandidateValidator.Valid(
        UUID.randomUUID(), 2, 3, "fx05-stale", valid.bundleDigest(), valid.candidateBytes(),
        valid.bundleBytes(), valid.manifestBytes());
    assertThatThrownBy(() -> store.prepare(staleParent))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PUBLICATION_PARENT_CONFLICT");
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app, "SELECT count(*) FROM content.publication_candidate WHERE operation_id='" + valid.operationId() + "'"))
          .isEqualTo(1);
      assertGoodPublicationHead(app);
    }
  }

  @Test
  void publicationRuntimeRoleCannotMutateImmutableRowsHeadOrReservation() throws Exception {
    try (Connection app = connect("app", SERVICE + "_db")) {
      String bundleId = text(app,
          "SELECT bundle_id FROM content.publication_candidate ORDER BY created_at DESC LIMIT 1");
      denied(app, "UPDATE content.publication_bundle SET bundle_digest=repeat('f',64) WHERE bundle_id='" + bundleId + "'");
      denied(app, "DELETE FROM content.publication_candidate WHERE bundle_id='" + bundleId + "'");
      denied(app, "UPDATE content.publication_canonical_head SET revision=2 WHERE singleton");
      denied(app, "UPDATE content.publication_candidate_reservation SET operation_id=NULL WHERE singleton");
      denied(app, "DELETE FROM content.publication_candidate_reservation WHERE singleton");
    }
  }

  @Test
  void publicationZCoordinatorCommitsFenceWithReleaseAndCompletesOnlyAfterReleasedReceipt()
      throws Exception {
    seedPublicationHead();
    releasePublicationReservation();
    provisionPublicationEnvironment();
    PublicationStore store = new PublicationStore(appDataSource(), PUBLICATION_CAPABILITY);
    var abandoned = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-abort", "abort-private", "Abort before canonical commit");
    store.prepare(abandoned);
    PublicationCoordinatorWire.Binding abandonedBinding = store.start(abandoned.operationId());
    store.requestAbort(abandoned.operationId());
    PublicationStore.Pending abandonedStage = store.pending(abandoned.operationId());
    assertThat(abandonedStage.phase()).isEqualTo("STAGE");
    store.accept(
        abandoned.operationId(),
        "STAGE",
        publicationReceipt(abandonedBinding, abandonedStage, "STAGED"));
    PublicationStore.Pending abort = store.pending(abandoned.operationId());
    assertThat(abort.phase()).isEqualTo("ABORT");
    store.accept(
        abandoned.operationId(),
        "ABORT",
        publicationReceipt(abandonedBinding, abort, "ABORTED"));
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(1);
      assertThat(text(app,
          "SELECT state FROM content.publication_operation WHERE operation_id='"
              + abandoned.operationId() + "'"))
          .isEqualTo("ABORTED");
    }

    var fencedAbort = PublicationFixtureSupport.variant(
        UUID.randomUUID(),
        "fx05-fence-abort",
        "fence-abort-private",
        "Abort after FENCE command exists");
    store.prepare(fencedAbort);
    PublicationCoordinatorWire.Binding fencedAbortBinding = store.start(fencedAbort.operationId());
    PublicationStore.Pending stagedPrefix = store.pending(fencedAbort.operationId());
    store.accept(
        fencedAbort.operationId(),
        "STAGE",
        publicationReceipt(fencedAbortBinding, stagedPrefix, "STAGED"));
    PublicationStore.Pending fencePrefix = store.pending(fencedAbort.operationId());
    assertThat(fencePrefix.phase()).isEqualTo("FENCE");
    store.requestAbort(fencedAbort.operationId());
    assertThat(store.pending(fencedAbort.operationId()).phase()).isEqualTo("FENCE");
    store.accept(
        fencedAbort.operationId(),
        "FENCE",
        publicationReceipt(fencedAbortBinding, fencePrefix, "FENCED"));
    PublicationStore.Pending abortAfterFence = store.pending(fencedAbort.operationId());
    assertThat(abortAfterFence.phase()).isEqualTo("ABORT");
    store.accept(
        fencedAbort.operationId(),
        "ABORT",
        publicationReceipt(fencedAbortBinding, abortAfterFence, "ABORTED"));
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(1);
    }

    var valid = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-coordinator", "coordinator-private", "Coordinator candidate");
    store.prepare(valid);
    PublicationCoordinatorWire.Binding expectedBinding = new PublicationCoordinatorWire.Binding(
        valid.operationId(),
        valid.parent(),
        valid.target(),
        valid.bundleId(),
        valid.bundleDigest(),
        sha256(valid.manifestBytes()),
        UUID.fromString("00000000-0000-4000-8000-000000000071"),
        UUID.fromString("00000000-0000-4000-8000-000000000072"),
        UUID.fromString("00000000-0000-4000-8000-000000000073"));
    long expectedStartSequence;
    try (Connection app = connect("app", SERVICE + "_db")) {
      expectedStartSequence = number(app,
          "SELECT last_delivery_sequence+1 "
              + "FROM content.publication_environment WHERE singleton");
    }
    var expectedStage = PublicationCoordinatorWire.command(
        expectedBinding,
        "STAGE",
        expectedStartSequence);
    try (Connection app = connect("app", SERVICE + "_db")) {
      SQLException startDigestMismatch = assertThrows(SQLException.class, () -> directStart(
          app,
          valid.operationId(),
          expectedStage.bytes(),
          "0".repeat(64),
          PUBLICATION_CAPABILITY));
      assertThat(startDigestMismatch.getMessage())
          .contains("PUBLICATION_COMMAND_DIGEST_MISMATCH");
    }
    PublicationCoordinatorWire.Binding binding = store.start(valid.operationId());

    PublicationStore.Pending stage = store.pending(valid.operationId());
    assertThat(stage.phase()).isEqualTo("STAGE");
    assertThat(stage.sequence()).isEqualTo(expectedStartSequence);
    var directFence = PublicationCoordinatorWire.command(
        binding,
        "FENCE",
        stage.sequence() + 1);
    try (Connection app = connect("app", SERVICE + "_db")) {
      SQLException bypass = assertThrows(SQLException.class, () -> directAccept(
          app,
          valid.operationId(),
          "STAGE",
          "{}".getBytes(StandardCharsets.UTF_8),
          directFence.bytes(),
          directFence.digest(),
          "wrong-general-app-capability-value".getBytes(StandardCharsets.UTF_8)));
      assertThat(bypass.getMessage()).contains("PUBLICATION_COORDINATOR_CAPABILITY_DENIED");
      SQLException missingCapability = assertThrows(SQLException.class, () -> directAccept(
          app,
          valid.operationId(),
          "STAGE",
          publicationReceipt(binding, stage, "STAGED"),
          directFence.bytes(),
          directFence.digest(),
          null));
      assertThat(missingCapability.getMessage())
          .contains("PUBLICATION_COORDINATOR_CAPABILITY_DENIED");

      var wrongKeys = WireCodec.JSON.createObjectNode();
      for (int index = 0; index < 14; index++) {
        wrongKeys.put("unknown" + index, index);
      }
      SQLException wrongKeySet = assertThrows(SQLException.class, () -> directAccept(
          app,
          valid.operationId(),
          "STAGE",
          WireCodec.JSON.writeValueAsBytes(wrongKeys),
          directFence.bytes(),
          directFence.digest(),
          PUBLICATION_CAPABILITY));
      assertThat(wrongKeySet.getMessage()).contains("PUBLICATION_RECEIPT_BINDING_CONFLICT");

      SQLException receiptDigestMismatch = assertThrows(SQLException.class, () ->
          directAcceptWithDigest(
              app,
              valid.operationId(),
              "STAGE",
              publicationReceipt(binding, stage, "STAGED"),
              "0".repeat(64),
              directFence.bytes(),
              directFence.digest(),
              PUBLICATION_CAPABILITY));
      assertThat(receiptDigestMismatch.getMessage())
          .contains("PUBLICATION_RECEIPT_DIGEST_MISMATCH");

      SQLException withoutClaim = assertThrows(SQLException.class, () -> directAccept(
          app,
          valid.operationId(),
          "STAGE",
          publicationReceipt(binding, stage, "STAGED"),
          directFence.bytes(),
          directFence.digest(),
          PUBLICATION_CAPABILITY));
      assertThat(withoutClaim.getMessage()).contains("PUBLICATION_DELIVERY_LEASE_DENIED");

      SQLException nullClaim = assertThrows(SQLException.class, () -> directClaim(
          app,
          valid.operationId(),
          "STAGE",
          null,
          PUBLICATION_CAPABILITY));
      assertThat(nullClaim.getMessage())
          .contains("PUBLICATION_DELIVERY_LEASE_TOKEN_REQUIRED");
    }
    PublicationStore.LeasedPending stageLease = store.lease(valid.operationId());
    try (Connection app = connect("app", SERVICE + "_db")) {
      SQLException nextDigestMismatch = assertThrows(SQLException.class, () ->
          directAcceptWithLease(
              app,
              valid.operationId(),
              "STAGE",
              publicationReceipt(binding, stage, "STAGED"),
              directFence.bytes(),
              "0".repeat(64),
              stageLease.leaseToken(),
              PUBLICATION_CAPABILITY));
      assertThat(nextDigestMismatch.getMessage())
          .contains("PUBLICATION_NEXT_COMMAND_DIGEST_MISMATCH");

      SQLException wrongLease = assertThrows(SQLException.class, () -> directAcceptWithLease(
          app,
          valid.operationId(),
          "STAGE",
          publicationReceipt(binding, stage, "STAGED"),
          directFence.bytes(),
          directFence.digest(),
          UUID.randomUUID(),
          PUBLICATION_CAPABILITY));
      assertThat(wrongLease.getMessage()).contains("PUBLICATION_DELIVERY_LEASE_DENIED");
    }
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "UPDATE content.publication_phase_outbox "
              + "SET lease_until=clock_timestamp()-interval '1 second' "
              + "WHERE operation_id='" + valid.operationId() + "' AND phase='STAGE'");
    }
    try (Connection app = connect("app", SERVICE + "_db")) {
      SQLException expiredLease = assertThrows(SQLException.class, () -> directAcceptWithLease(
          app,
          valid.operationId(),
          "STAGE",
          publicationReceipt(binding, stage, "STAGED"),
          directFence.bytes(),
          directFence.digest(),
          stageLease.leaseToken(),
          PUBLICATION_CAPABILITY));
      assertThat(expiredLease.getMessage()).contains("PUBLICATION_DELIVERY_LEASE_DENIED");
      assertThat(text(app,
          "SELECT state FROM content.publication_operation WHERE operation_id='"
              + valid.operationId() + "'"))
          .isEqualTo("STAGE_PENDING");
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_receipt WHERE operation_id='"
              + valid.operationId() + "'"))
          .isZero();
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_outbox WHERE operation_id='"
              + valid.operationId() + "' AND phase='FENCE'"))
          .isZero();
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(1);
    }
    PublicationStore.LeasedPending replacementStageLease = store.lease(valid.operationId());
    store.accept(
        replacementStageLease,
        publicationReceipt(binding, stage, "STAGED"));
    PublicationStore.Pending fence = store.pending(valid.operationId());
    assertThat(fence.phase()).isEqualTo("FENCE");
    PublicationStore.LeasedPending fencedLease = store.lease(valid.operationId());

    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "CREATE FUNCTION content.inject_release_outbox_fault() RETURNS trigger "
              + "LANGUAGE plpgsql AS $$ BEGIN IF NEW.phase='RELEASE' THEN "
              + "RAISE EXCEPTION 'RELEASE_OUTBOX_FAULT' USING ERRCODE='40001'; "
              + "END IF; RETURN NEW; END $$");
      execute(migrator,
          "CREATE TRIGGER publication_release_outbox_fault BEFORE INSERT "
              + "ON content.publication_phase_outbox FOR EACH ROW "
              + "EXECUTE FUNCTION content.inject_release_outbox_fault()");
    }
    try {
      assertThatThrownBy(() -> store.accept(
          fencedLease, publicationReceipt(binding, fence, "FENCED")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("RELEASE_OUTBOX_FAULT");
    } finally {
      try (Connection migrator = connect("migrator", SERVICE + "_db")) {
        execute(migrator,
            "DROP TRIGGER publication_release_outbox_fault "
                + "ON content.publication_phase_outbox");
        execute(migrator, "DROP FUNCTION content.inject_release_outbox_fault()");
      }
    }
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(1);
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_receipt "
              + "WHERE operation_id='" + valid.operationId() + "' AND phase='FENCE'"))
          .isZero();
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_outbox "
              + "WHERE operation_id='" + valid.operationId() + "' AND phase='RELEASE'"))
          .isZero();
    }

    store.accept(fencedLease, publicationReceipt(binding, fence, "FENCED"));
    PublicationStore.Pending release = store.pending(valid.operationId());
    assertThat(release.phase()).isEqualTo("RELEASE");
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(2);
      assertThat(number(app,
          "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
          .isEqualTo(1);
      assertThat(text(app,
          "SELECT state FROM content.publication_operation WHERE operation_id='"
              + valid.operationId() + "'"))
          .isEqualTo("RELEASE_PENDING");
    }

    store.accept(valid.operationId(), "RELEASE", publicationReceipt(binding, release, "RELEASED"));
    assertThat(store.pending(valid.operationId())).isNull();
    assertThatThrownBy(() -> store.requestAbort(valid.operationId()))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("PUBLICATION_ABORT_PREFIX_OR_COMMIT_DENIED");
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT learning_ready_revision FROM content.publication_environment WHERE singleton"))
          .isEqualTo(2);
      assertThat(text(app,
          "SELECT state FROM content.publication_operation WHERE operation_id='"
              + valid.operationId() + "'"))
          .isEqualTo("COMPLETE");
      assertThat(text(app,
          "SELECT operation_id::text FROM content.publication_candidate_reservation WHERE singleton"))
          .isNull();
      // Ba operation liên tiếp phải dùng một sequence global; nếu reset về 1
      // Learning content-global cursor sẽ từ chối publication kế tiếp.
      assertThat(number(app,
          "SELECT count(DISTINCT delivery_sequence) "
              + "FROM content.publication_phase_outbox"))
          .isEqualTo(8);
      assertThat(number(app,
          "SELECT last_delivery_sequence "
              + "FROM content.publication_environment WHERE singleton"))
          .isEqualTo(8);
      denied(app, "UPDATE content.publication_operation SET state='ABORTED' "
          + "WHERE operation_id='" + valid.operationId() + "'");
      denied(app, "DELETE FROM content.publication_phase_receipt WHERE operation_id='"
          + valid.operationId() + "'");
    }
  }

  @Test
  void publicationZZCoordinatorConcurrentStartAndLeaseConvergeWithoutDuplicatePrefix()
      throws Exception {
    // Test trước đã hoàn tất revision 2. Migrator chỉ reset canonical fixture trong
    // disposable lab để race này có candidate parent=1 độc lập; production role
    // không có quyền hạ head hoặc readiness.
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "UPDATE content.publication_canonical_head "
              + "SET revision=1,bundle_id='fx05-p1',bundle_digest=repeat('0',64) "
              + "WHERE singleton");
    }
    releasePublicationReservation();
    provisionPublicationEnvironment();

    var candidate = PublicationFixtureSupport.variant(
        UUID.randomUUID(),
        "fx05-coordinator-race",
        "coordinator-race-private",
        "Coordinator two-connection race");
    PublicationStore store = new PublicationStore(appDataSource(), PUBLICATION_CAPABILITY);
    store.prepare(candidate);

    var executor = Executors.newFixedThreadPool(2);
    try {
      var startGate = new CountDownLatch(1);
      var firstStart = executor.submit(
          () -> coordinatorStartResult(store, candidate.operationId(), startGate));
      var secondStart = executor.submit(
          () -> coordinatorStartResult(store, candidate.operationId(), startGate));
      startGate.countDown();
      assertThat(List.of(
          firstStart.get(10, TimeUnit.SECONDS),
          secondStart.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "OK");

      PublicationStore.Pending exactStage = store.pending(candidate.operationId());
      assertThat(exactStage).isNotNull();
      assertThat(exactStage.phase()).isEqualTo("STAGE");
      // Retry sau race phải trả đúng binding/prefix cũ, không tăng global cursor.
      store.start(candidate.operationId());
      assertExactPending(store.pending(candidate.operationId()), exactStage);

      var leaseGate = new CountDownLatch(1);
      var firstLease = executor.submit(
          () -> coordinatorLeaseResult(store, candidate.operationId(), leaseGate));
      var secondLease = executor.submit(
          () -> coordinatorLeaseResult(store, candidate.operationId(), leaseGate));
      leaseGate.countDown();
      List<String> leases = List.of(
          firstLease.get(10, TimeUnit.SECONDS),
          secondLease.get(10, TimeUnit.SECONDS));
      assertThat(leases.stream().filter("OK"::equals).count()).isEqualTo(1);
      assertThat(leases.stream()
          .filter(result -> result.contains("PUBLICATION_DELIVERY_LEASE_CONFLICT"))
          .count()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "UPDATE content.publication_phase_outbox "
              + "SET lease_until=clock_timestamp()-interval '1 second' "
              + "WHERE operation_id='" + candidate.operationId() + "' AND phase='STAGE'");
    }
    PublicationStore.LeasedPending recovered = store.lease(candidate.operationId());
    assertExactPending(recovered.pending(), store.pending(candidate.operationId()));
    try (Connection app = connect("app", SERVICE + "_db")) {
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_operation WHERE operation_id='"
              + candidate.operationId() + "'"))
          .isOne();
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_outbox WHERE operation_id='"
              + candidate.operationId() + "' AND phase='STAGE'"))
          .isOne();
      assertThat(number(app,
          "SELECT count(*) FROM content.publication_phase_receipt WHERE operation_id='"
              + candidate.operationId() + "'"))
          .isZero();
      assertThat(number(app,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(1);
    }
  }

  @Test
  void twoConnectionsDoNotReadUncommittedWritesAndRollbackPreservesData() throws Exception {
    String id = seed();
    try (Connection first = connect("app", SERVICE + "_db"); Connection second = connect("app", SERVICE + "_db")) {
      first.setAutoCommit(false);
      execute(first, "UPDATE " + SERVICE + ".transaction_probe SET payload='uncommitted' WHERE id='" + id + "'");
      assertThat(text(second, "SELECT payload FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo("initial");
      first.rollback();
      assertThat(text(second, "SELECT payload FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo("initial");
    }
  }

  @Test
  void twoConnectionsUseTheDatabaseRowLockBeforeTheCasLoserReturns() throws Exception {
    String id = seed();
    var executor = Executors.newSingleThreadExecutor();
    CountDownLatch ready = new CountDownLatch(1);
    AtomicInteger waitingPid = new AtomicInteger();
    try (Connection first = connect("app", SERVICE + "_db")) {
      first.setAutoCommit(false);
      int firstPid = number(first, "SELECT pg_backend_pid()");
      execute(first, "UPDATE " + SERVICE + ".transaction_probe SET revision=1,payload='winner' WHERE id='" + id + "'");
      var loser = executor.submit(() -> {
        try (Connection second = connect("app", SERVICE + "_db")) {
          waitingPid.set(number(second, "SELECT pg_backend_pid()"));
          ready.countDown();
          return execute(second, "UPDATE " + SERVICE + ".transaction_probe SET revision=revision+1,payload='loser' WHERE id='" + id + "' AND revision=0");
        }
      });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      boolean blocked = false;
      while (System.nanoTime() < deadline) {
        try (var statement = first.createStatement(); var result = statement.executeQuery("SELECT pg_blocking_pids(" + waitingPid.get() + ")")) {
          result.next();
          blocked = Arrays.asList((Integer[]) result.getArray(1).getArray()).contains(firstPid);
        }
        if (blocked) { break; }
      }
      assertThat(blocked).as("server-observed row lock, not a guessed sleep").isTrue();
      assertThat(loser.isDone()).isFalse();
      first.commit();
      assertThat(loser.get(5, TimeUnit.SECONDS)).isZero();
      assertThat(text(first, "SELECT payload FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo("winner");
      first.rollback();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void twoConnectionsWithLockTimeoutRollbackTheLosingTransaction() throws Exception {
    String id = seed();
    try (Connection first = connect("app", SERVICE + "_db"); Connection second = connect("app", SERVICE + "_db")) {
      first.setAutoCommit(false); second.setAutoCommit(false);
      execute(first, "UPDATE " + SERVICE + ".transaction_probe SET payload='held' WHERE id='" + id + "'");
      execute(second, "SET LOCAL lock_timeout='200ms'");
      SQLException failure = assertThrows(SQLException.class, () -> execute(second,
          "UPDATE " + SERVICE + ".transaction_probe SET payload='timeout' WHERE id='" + id + "'"));
      assertThat(failure.getSQLState()).isEqualTo("55P03");
      second.rollback(); first.rollback();
      assertThat(text(second, "SELECT payload FROM " + SERVICE + ".transaction_probe WHERE id='" + id + "'")).isEqualTo("initial");
    }
  }

  @Test
  void zzFailedMigrationRollsBackDdlAndHistoryBeforeRetry() throws Exception {
    assertThatThrownBy(() -> flyway("migrator", "classpath:db/migration", "classpath:db-test-migration", "classpath:db-failure").migrate())
        .isInstanceOf(FlywayException.class);
    try (Connection connection = connect("migrator", SERVICE + "_db")) {
      assertThat(text(connection, "SELECT to_regclass('" + SERVICE + ".failed_probe')::text")).isNull();
      assertThat(number(connection, "SELECT count(*) FROM migration_metadata.flyway_schema_history WHERE version='101'")).isZero();
      assertThat(text(connection, "SELECT service_key FROM " + SERVICE + ".service_descriptor")).isEqualTo(SERVICE);
    }
    var repaired = flyway("migrator", "classpath:db/migration", "classpath:db-test-migration", "classpath:db-recovery");
    assertThat(repaired.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(repaired.migrate().migrationsExecuted).isZero();
    repaired.validate();
  }

  private static String seed() throws SQLException {
    String id = UUID.randomUUID().toString();
    try (Connection connection = connect("app", SERVICE + "_db")) {
      execute(connection, "INSERT INTO " + SERVICE + ".transaction_probe VALUES ('" + id + "',0,'initial')");
    }
    return id;
  }

  private static Connection connect(String kind, String database) throws SQLException {
    Properties properties = new Properties();
    properties.setProperty("user", CONFIG.getProperty(SERVICE + "." + kind + ".user"));
    properties.setProperty("password", CONFIG.getProperty(SERVICE + "." + kind + ".password"));
    properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "15");
    properties.setProperty("sslmode", "disable"); // Explicit disposable loopback DB lab, not production TLS.
    return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + CONFIG.getProperty("lab.port") + "/" + database, properties);
  }

  private static PGSimpleDataSource appDataSource(){
    PGSimpleDataSource ds=new PGSimpleDataSource();ds.setServerNames(new String[]{"127.0.0.1"});ds.setPortNumbers(new int[]{Integer.parseInt(CONFIG.getProperty("lab.port"))});ds.setDatabaseName(SERVICE+"_db");ds.setUser(CONFIG.getProperty(SERVICE+".app.user"));ds.setPassword(CONFIG.getProperty(SERVICE+".app.password"));ds.setConnectTimeout(5);ds.setSocketTimeout(15);ds.setSslMode("disable");return ds;
  }
  private static void seedPublicationHead() throws SQLException {
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "INSERT INTO content.publication_canonical_head(revision,bundle_id,bundle_digest) "
              + "VALUES(1,'fx05-p1',repeat('0',64)) ON CONFLICT DO NOTHING");
    }
  }

  private static CandidateValidator.Valid allocationCandidate(
      byte[] bundle, UUID operationId, String bundleId) throws Exception {
    String caseId = WireCodec.JSON.readTree(bundle).path("id").asString();
    byte[] candidate = PublicationFixtureSupport.candidateForBundle(bundle, root -> {
      root.put("operationId", operationId.toString());
      ((ObjectNode) root.path("bundle")).put("id", bundleId);
      for (var component : root.path("components")) {
        if (component.path("type").asString().equals("case")) {
          ((ObjectNode) component).put("id", caseId);
        }
      }
    });
    return new CandidateValidator().validate(candidate, bundle);
  }

  private static void releasePublicationReservation() throws SQLException {
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "UPDATE content.publication_candidate_reservation "
              + "SET generation=generation+1,operation_id=NULL WHERE singleton");
    }
  }

  private static void provisionPublicationEnvironment() throws SQLException {
    try (Connection migrator = connect("migrator", SERVICE + "_db")) {
      execute(migrator,
          "INSERT INTO content.publication_environment("
              + "singleton,realm_id,producer_generation,participant_generation,"
              + "coordinator_capability_sha256,learning_ready_revision) VALUES("
              + "true,'00000000-0000-4000-8000-000000000071',"
              + "'00000000-0000-4000-8000-000000000072',"
              + "'00000000-0000-4000-8000-000000000073',"
              + "sha256(convert_to('publication-coordinator-test-capability-v1','UTF8')),1) "
              + "ON CONFLICT(singleton) DO UPDATE SET learning_ready_revision=1");
    }
  }

  private static byte[] publicationReceipt(
      PublicationCoordinatorWire.Binding binding,
      PublicationStore.Pending pending,
      String state) {
    var receipt = WireCodec.JSON.createObjectNode();
    receipt.put("contractVersion", 1);
    receipt.put("operationId", binding.operationId().toString());
    receipt.put("phase", pending.phase());
    receipt.put("state", state);
    receipt.put("parentRevision", binding.parentRevision());
    receipt.put("targetRevision", binding.targetRevision());
    receipt.put("bundleId", binding.bundleId());
    receipt.put("bundleSha256", binding.bundleSha256());
    receipt.put("manifestSha256", binding.manifestSha256());
    receipt.put("realmId", binding.realmId().toString());
    receipt.put("producerGeneration", binding.producerGeneration().toString());
    receipt.put("participantGeneration", binding.participantGeneration().toString());
    receipt.put("deliverySequence", pending.sequence());
    receipt.put("commandSha256", pending.commandDigest());
    return WireCodec.JSON.writeValueAsBytes(receipt);
  }

  private static void directAccept(
      Connection connection,
      UUID operationId,
      String phase,
      byte[] receipt,
      byte[] nextCommand,
      String nextDigest,
      byte[] capability) throws SQLException {
    directAcceptWithDigest(
        connection,
        operationId,
        phase,
        receipt,
        sha256(receipt),
        nextCommand,
        nextDigest,
        capability);
  }

  private static void directStart(
      Connection connection,
      UUID operationId,
      byte[] command,
      String commandDigest,
      byte[] capability) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.start_publication(?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setBytes(2, command);
      statement.setString(3, commandDigest);
      statement.setBytes(4, capability);
      statement.executeQuery().close();
    }
  }

  private static void directAcceptWithDigest(
      Connection connection,
      UUID operationId,
      String phase,
      byte[] receipt,
      String receiptDigest,
      byte[] nextCommand,
      String nextDigest,
      byte[] capability) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.accept_publication_receipt(?,?,?,?,?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setString(2, phase);
      statement.setBytes(3, receipt);
      statement.setString(4, receiptDigest);
      statement.setBytes(5, nextCommand);
      statement.setString(6, nextDigest);
      statement.setNull(7, java.sql.Types.OTHER);
      statement.setBytes(8, capability);
      statement.executeQuery().close();
    }
  }

  private static void directClaim(
      Connection connection,
      UUID operationId,
      String phase,
      UUID leaseToken,
      byte[] capability) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.claim_publication_delivery(?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setString(2, phase);
      statement.setObject(3, leaseToken);
      statement.setBytes(4, capability);
      statement.executeQuery().close();
    }
  }

  private static void directAcceptWithLease(
      Connection connection,
      UUID operationId,
      String phase,
      byte[] receipt,
      byte[] nextCommand,
      String nextDigest,
      UUID leaseToken,
      byte[] capability) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.accept_publication_receipt(?,?,?,?,?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setString(2, phase);
      statement.setBytes(3, receipt);
      statement.setString(4, sha256(receipt));
      statement.setBytes(5, nextCommand);
      statement.setString(6, nextDigest);
      statement.setObject(7, leaseToken);
      statement.setBytes(8, capability);
      statement.executeQuery().close();
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception failure) {
      throw new IllegalStateException("SHA-256 unavailable", failure);
    }
  }

  private static String prepareResult(
      PublicationStore store, CandidateValidator.Valid candidate, CountDownLatch start) throws Exception {
    start.await();
    try {
      store.prepare(candidate);
      return "OK";
    } catch (SQLException conflict) {
      return conflict.getMessage();
    }
  }

  private static String coordinatorStartResult(
      PublicationStore store, UUID operationId, CountDownLatch start) throws Exception {
    start.await();
    try {
      store.start(operationId);
      return "OK";
    } catch (SQLException conflict) {
      return conflict.getMessage();
    }
  }

  private static String coordinatorLeaseResult(
      PublicationStore store, UUID operationId, CountDownLatch start) throws Exception {
    start.await();
    try {
      store.lease(operationId);
      return "OK";
    } catch (SQLException conflict) {
      return conflict.getMessage();
    }
  }

  private static void assertExactPending(
      PublicationStore.Pending actual, PublicationStore.Pending expected) {
    assertThat(actual.operationId()).isEqualTo(expected.operationId());
    assertThat(actual.state()).isEqualTo(expected.state());
    assertThat(actual.phase()).isEqualTo(expected.phase());
    assertThat(actual.sequence()).isEqualTo(expected.sequence());
    assertThat(actual.commandDigest()).isEqualTo(expected.commandDigest());
    // byte[] trong record dùng identity equality; protocol cần so exact content.
    assertThat(actual.commandBytes()).containsExactly(expected.commandBytes());
  }

  private static void assertGoodPublicationHead(Connection connection) throws SQLException {
    assertThat(number(connection,
        "SELECT revision FROM content.publication_canonical_head WHERE singleton")).isEqualTo(1);
    assertThat(text(connection,
        "SELECT bundle_digest FROM content.publication_canonical_head WHERE singleton"))
        .isEqualTo("0".repeat(64));
  }

  private static Flyway migrations() { return flyway("migrator", "classpath:db/migration", "classpath:db-test-migration"); }

  private static Flyway flyway(String kind, String... locations) {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {"127.0.0.1"});
    dataSource.setPortNumbers(new int[] {Integer.parseInt(CONFIG.getProperty("lab.port"))});
    dataSource.setDatabaseName(SERVICE + "_db");
    dataSource.setUser(CONFIG.getProperty(SERVICE + "." + kind + ".user"));
    dataSource.setPassword(CONFIG.getProperty(SERVICE + "." + kind + ".password"));
    dataSource.setConnectTimeout(5); dataSource.setSocketTimeout(15); dataSource.setSslMode("disable");
    return Flyway.configure().dataSource(dataSource).defaultSchema("migration_metadata")
        .schemas("migration_metadata").createSchemas(false).cleanDisabled(true).locations(locations).load();
  }

  private static void denied(Connection connection, String sql) {
    SQLException error = assertThrows(SQLException.class, () -> execute(connection, sql));
    assertThat(error.getSQLState()).isEqualTo("42501");
  }

  private static int execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) { return statement.executeUpdate(sql); }
  }

  private static int number(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getInt(1); }
  }

  private static String text(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) { result.next(); return result.getString(1); }
  }
}
