package lab.backend.learning.work;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lab.backend.learning.contentreplica.PublicationGate;
import lab.backend.learning.contentreplica.PublicationStore;
import lab.backend.learning.contentreplica.PublicationWire;
import lab.backend.learning.contentreplica.ReplicaValidator;
import lab.backend.learning.exposure.ExposureJournalPump;
import lab.backend.learning.journal.FilesystemJournalStore;
import lab.backend.learning.journal.JournalStore;
import lab.backend.learning.transport.WireCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;

/** Actual own PostgreSQL/filesystem; core callback tests không thay packaged canonical admission. */
@Timeout(40)
class AllocationStoreIT {
  private static final UUID REALM = UUID.randomUUID();
  private static final UUID GENERATION = UUID.randomUUID();
  private static final UUID CONTENT = UUID.randomUUID();
  private static final Properties CONFIG = new Properties();
  private static PGSimpleDataSource app;
  private static PGSimpleDataSource migrator;
  private static AllocationStore store;
  private static byte[] manifest;
  private static long sequence;
  private static long revision = 1;
  @TempDir Path journalDirectory;

  @BeforeAll
  static void seedVerifiedReleasedReplicaViaParticipant() throws Exception {
    String file = System.getProperty("lab.config");
    if (file == null || file.startsWith("${")) throw new IllegalStateException("Owned PG required");
    try (var reader = Files.newBufferedReader(Path.of(file).toRealPath())) {
      CONFIG.load(reader);
    }
    app = source("app");
    migrator = source("migrator");
    store = new AllocationStore(REALM, GENERATION);
    sql("INSERT INTO learning.publication_environment VALUES (true,'" + REALM + "','"
        + GENERATION + "','" + CONTENT + "',true)");
    sql("INSERT INTO learning.publication_head(singleton,applied_revision) VALUES(true,1)");
    sql("INSERT INTO learning.publication_delivery_cursor VALUES('content-global',0)");
    manifest = WireCodec.JSON.writeValueAsBytes(WireCodec.JSON.readTree(Files.readAllBytes(Path.of(
        "../../../contracts/fixtures/publication/fx05-candidate-cosmetic.json"))).path("oldVersions"));
    for (String name : new String[]{"a", "b", "practice"}) publish(name);
  }

  @Test
  void actualPreparingJournalVerifiedReadyPinsDraftZeroAndNeverLeaksExpected() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    UUID attempt = tx(c -> store.create(c, owner, key, request("assessment")));
    Map<String, Object> preparing = tx(c -> store.disclose(c, owner, attempt, false));
    assertEquals("preparing", preparing.get("state"));
    assertFalse(preparing.containsKey("prompt"));
    assertThrows(WireCodec.Denied.class,
        () -> tx(c -> { store.requireReadyForSubmission(c, owner, attempt); return null; }));
    var journal = new FilesystemJournalStore(journalDirectory.toAbsolutePath());
    var pump = new ExposureJournalPump(app, journal);
    assertTrue(pump.verify(attempt));
    Map<String, Object> ready = tx(c -> store.disclose(c, owner, attempt, true));
    assertEquals("ready", ready.get("state"));
    assertEquals(true, ready.get("independentAtAssignment"));
    assertEquals("0", ((Map<?, ?>) ready.get("draft")).get("revision"));
    String json = WireCodec.JSON.writeValueAsString(ready);
    assertFalse(json.contains("R08_PRIVATE_EXPECTED"));
    assertFalse(json.contains("rubricVersion"));
    assertFalse(json.contains("graderVersion"));
    tx(c -> { store.requireReadyForSubmission(c, owner, attempt); return null; });
    assertEquals(attempt, tx(c -> store.create(c, owner, key, request("assessment"))));
    assertEquals(attempt, tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment"))));
    assertEquals(1, count("allocation_attempt", owner));
    assertEquals(1, count("exposure_floor", owner));
    assertEquals(1, count("allocation_journal", owner));
  }

  @Test
  void sameKeyChangedPayloadAndForeignOwnerCannotReadOrConsumeAssignment() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    UUID attempt = tx(c -> store.create(c, owner, key, request("assessment")));
    var conflict = assertThrows(WireCodec.Denied.class,
        () -> tx(c -> store.create(c, owner, key, request("practice"))));
    assertEquals("KEY_REUSED", conflict.code);
    var foreign = assertThrows(WireCodec.Denied.class,
        () -> tx(c -> store.disclose(c, UUID.randomUUID(), attempt, true)));
    assertEquals(404, foreign.status);
    assertEquals(1, count("allocation_attempt", owner));
  }

  @Test
  void twoConnectionsAndStoreReplicasSerializeUnderActualLearnerGuard() throws Exception {
    UUID owner = UUID.randomUUID();
    sql("INSERT INTO learning.learner_guard(user_id) VALUES ('" + owner + "')");
    var other = new AllocationStore(REALM, GENERATION);
    try (var held = app.getConnection(); var pool = Executors.newFixedThreadPool(2)) {
      held.setAutoCommit(false);
      try (var statement = held.createStatement()) {
        statement.executeQuery("SELECT user_id FROM learning.learner_guard WHERE user_id='"
            + owner + "' FOR UPDATE").close();
      }
      UUID first = store.create(held, owner, UUID.randomUUID(), request("assessment"));
      var entered = new CountDownLatch(1);
      var second = pool.submit(() -> tx(c -> {
        entered.countDown();
        return other.create(c, owner, UUID.randomUUID(), request("assessment"));
      }));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      awaitDatabaseLockWait();
      assertFalse(second.isDone());
      held.commit();
      assertEquals(first, second.get(5, TimeUnit.SECONDS));
    }
    assertEquals(1, count("allocation_attempt", owner));
    assertEquals(1, count("exposure_floor", owner));
  }

  @Test
  void finitePoolKeepsExposureAcrossEpochAndNeverCreatesFakeClean() throws Exception {
    UUID owner = UUID.randomUUID();
    tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment")));
    sql("UPDATE learning.learner_guard SET learning_epoch=2 WHERE user_id='" + owner + "'");
    tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment")));
    sql("UPDATE learning.learner_guard SET learning_epoch=3 WHERE user_id='" + owner + "'");
    var failure = assertThrows(WireCodec.Denied.class,
        () -> tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment"))));
    assertEquals("CASE_EXHAUSTED", failure.code);
    assertEquals(2, count("allocation_attempt", owner));
    assertEquals(2, count("exposure_floor", owner));
    tx(c -> store.create(c, owner, UUID.randomUUID(), request("practice")));
    assertEquals(3, count("exposure_floor", owner));
  }

  @Test
  void lostAckAndPumpRestartReuseSameExternalRecordOutsideDatabaseLocks() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID attempt = tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment")));
    var actual = new FilesystemJournalStore(journalDirectory.toAbsolutePath());
    JournalStore lostAck = new JournalStore() {
      public Receipt append(String namespace, String recordId, byte[] bytes) throws JournalException {
        // DB lock NOWAIT trong journal callback chứng minh I/O không giữ learner guard.
        try (var connection = app.getConnection(); var statement = connection.createStatement()) {
          connection.setAutoCommit(false);
          statement.executeQuery("SELECT user_id FROM learning.learner_guard WHERE user_id='"
              + owner + "' FOR UPDATE NOWAIT").close();
          connection.rollback();
        } catch (SQLException heldLock) {
          throw new AssertionError("Journal I/O held learner guard", heldLock);
        }
        actual.append(namespace, recordId, bytes);
        throw new JournalException(ErrorCode.IO_FAILURE, "Simulated lost ACK after durable append");
      }
      public Inventory inventory(String namespace, String head) throws JournalException {
        return actual.inventory(namespace, head);
      }
    };
    assertFalse(new ExposureJournalPump(app, lostAck).verify(attempt));
    assertEquals("preparing", tx(c -> store.disclose(c, owner, attempt, false)).get("state"));
    assertTrue(new ExposureJournalPump(app,
        new FilesystemJournalStore(journalDirectory.toAbsolutePath())).verify(attempt));
    assertEquals(1, count("allocation_journal", owner));
    assertEquals("ready", tx(c -> store.disclose(c, owner, attempt, true)).get("state"));
    String fixedHead;
    try (var connection = app.getConnection(); var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT receipt->>'verifiedHeadDigest' "
            + "FROM learning.allocation_journal WHERE attempt_id='" + attempt + "'")) {
      rows.next();
      fixedHead = rows.getString(1);
    }
    var inventory = actual.inventory("learning-exposure-" + REALM, fixedHead);
    assertEquals(1, inventory.entries().size());
  }

  @Test
  void beforeAppendWrongReceiptAndLocalRollbackCannotDiscloseOrConsume() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID attempt = tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment")));
    var actual = new FilesystemJournalStore(journalDirectory.toAbsolutePath());
    JournalStore wrong = new JournalStore() {
      public Receipt append(String namespace, String recordId, byte[] bytes) throws JournalException {
        var receipt = actual.append(namespace, recordId, bytes);
        return new Receipt(namespace, UUID.randomUUID().toString(), receipt.recordDigest(),
            receipt.sequence(), receipt.nodeDigest(), receipt.verifiedHeadDigest(), 0);
      }
      public Inventory inventory(String namespace, String head) throws JournalException {
        return actual.inventory(namespace, head);
      }
    };
    assertFalse(new ExposureJournalPump(app, wrong).verify(attempt));
    assertFalse(tx(c -> store.disclose(c, owner, attempt, false)).containsKey("prompt"));
    assertThrows(WireCodec.Denied.class,
        () -> tx(c -> { store.requireReadyForSubmission(c, owner, attempt); return null; }));
    UUID rolledBack = UUID.randomUUID();
    try (var connection = app.getConnection()) {
      connection.setAutoCommit(false);
      store.create(connection, rolledBack, UUID.randomUUID(), request("assessment"));
      connection.rollback();
    }
    assertEquals(0, count("allocation_attempt", rolledBack));
    assertEquals(0, count("exposure_floor", rolledBack));
  }

  @Test
  void epochLifecycleFenceAndRuntimeRoleConstraintsFailClosed() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID attempt = tx(c -> store.create(c, owner, UUID.randomUUID(), request("assessment")));
    try {
      sql("UPDATE learning.publication_head SET fence_operation='" + UUID.randomUUID() + "'");
      assertThrows(WireCodec.Denied.class, () -> tx(c -> store.locate(c, owner, attempt)));
    } finally {
      sql("UPDATE learning.publication_head SET fence_operation=NULL");
    }
    sql("UPDATE learning.learner_guard SET lifecycle_state='PENDING' WHERE user_id='" + owner + "'");
    assertThrows(WireCodec.Denied.class, () -> tx(c -> store.locate(c, owner, attempt)));
    sql("UPDATE learning.learner_guard SET lifecycle_state='ACTIVE',learning_epoch=2 "
        + "WHERE user_id='" + owner + "'");
    assertThrows(WireCodec.Denied.class, () -> tx(c -> store.locate(c, owner, attempt)));
    for (String mutation : new String[]{
        "UPDATE learning.allocation_attempt SET case_version='forged' WHERE attempt_id='" + attempt + "'",
        "DELETE FROM learning.exposure_floor WHERE user_id='" + owner + "'",
        "UPDATE learning.allocation_attempt SET state='READY' WHERE attempt_id='" + attempt + "'",
        "UPDATE learning.allocation_draft SET revision=1 WHERE attempt_id='" + attempt + "'"}) {
      assertThrows(SQLException.class, () -> execute(app, mutation));
    }
  }

  private static void publish(String name) throws Exception {
    byte[] bundle = Files.readAllBytes(Path.of(
        "../../../contracts/fixtures/exposure/r08-private-" + name + "-v3.json"));
    String digest = PublicationWire.sha256(bundle);
    String manifestDigest = PublicationWire.sha256(manifest);
    var publication = new PublicationStore(app, REALM, GENERATION, new PublicationGate());
    UUID operation = UUID.randomUUID();
    for (String phase : new String[]{"STAGE", "FENCE", "RELEASE"}) {
      var command = new PublicationWire.Command(1, operation, phase, revision, revision + 1,
          "r08-" + name, digest, manifestDigest, REALM, CONTENT, GENERATION, ++sequence,
          AllocationWire.sha((operation + phase).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      if (phase.equals("STAGE")) {
        publication.stage(command, new ReplicaValidator().verify(bundle, manifest, digest, manifestDigest));
      } else publication.phase(command);
    }
    revision++;
  }

  private static void awaitDatabaseLockWait() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < deadline) {
      try (var connection = migrator.getConnection(); var statement = connection.createStatement();
          var rows = statement.executeQuery("SELECT count(*) FROM pg_locks WHERE NOT granted")) {
        rows.next();
        if (rows.getInt(1) > 0) return;
      }
      Thread.sleep(20);
    }
    fail("Second actual DB connection did not reach lock barrier");
  }

  private static AllocationWire.Create request(String mode) {
    return AllocationWire.create(WireCodec.JSON.writeValueAsBytes(Map.of(
        "schemaVersion", 1, "exerciseId", "EXERCISE-R08-GROUPING", "requestedMode", mode)));
  }

  private static <T> T tx(Sql<T> callback) throws Exception {
    try (var connection = app.getConnection()) {
      connection.setAutoCommit(false);
      try {
        T value = callback.run(connection);
        connection.commit();
        return value;
      } catch (Exception failure) {
        connection.rollback();
        throw failure;
      }
    }
  }

  private static long count(String table, UUID owner) throws Exception {
    try (var connection = app.getConnection(); var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT count(*) FROM learning." + table + " WHERE user_id='" + owner + "'")) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static PGSimpleDataSource source(String kind) {
    var result = new PGSimpleDataSource();
    result.setServerNames(new String[]{"127.0.0.1"});
    result.setPortNumbers(new int[]{Integer.parseInt(CONFIG.getProperty("lab.port"))});
    result.setDatabaseName("learning_db");
    result.setUser(CONFIG.getProperty("learning." + kind + ".user"));
    result.setPassword(CONFIG.getProperty("learning." + kind + ".password"));
    result.setConnectTimeout(1);
    result.setSocketTimeout(8);
    result.setSslMode("disable");
    return result;
  }

  private static void sql(String statement) throws Exception {
    execute(migrator, statement);
  }

  private static void execute(PGSimpleDataSource source, String sql) throws SQLException {
    try (var connection = source.getConnection(); var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  @FunctionalInterface
  private interface Sql<T> {
    T run(Connection connection) throws Exception;
  }
}
