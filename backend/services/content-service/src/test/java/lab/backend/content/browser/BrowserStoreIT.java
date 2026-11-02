package lab.backend.content.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;
import java.util.UUID;
import lab.backend.content.publication.PublicationDigest;
import lab.backend.content.transport.WireCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Kiểm browser publication binding và MVCC read trên actual owned PostgreSQL.
 *
 * <p>Input là disposable DB sau các suite nền; fixture chỉ ghi test rows. Output
 * chứng minh least privilege, candidate/FENCE commitment, temporal gate và
 * head/snapshot consistency; failure luôn rollback hoặc fail closed.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class BrowserStoreIT {
  private static PGSimpleDataSource app;
  private static PGSimpleDataSource migrator;
  private static byte[] snapshot;

  @BeforeAll
  static void setup() throws Exception {
    String input = System.getProperty("lab.config");
    if (input == null || input.startsWith("${")) {
      throw new IllegalStateException("Actual owned PostgreSQL is required");
    }
    Properties lab = new Properties();
    try (var reader = Files.newBufferedReader(Path.of(input))) {
      lab.load(reader);
    }
    app = source(lab, "app");
    migrator = source(lab, "migrator");
    Flyway.configure()
        .dataSource(migrator)
        .schemas("migration_metadata")
        .defaultSchema("migration_metadata")
        .createSchemas(false)
        .cleanDisabled(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    snapshot =
        Files.readAllBytes(
            Path.of(
                BrowserStoreIT.class
                    .getResource("/contracts/content/public/fixtures/browser-snapshot-v1.json")
                    .toURI()));
    seed();
  }

  @Test
  void aaReadsCanonicalReadinessAndProjectionFromActualDatabase() {
    BrowserStore store = new BrowserStore(app);
    store.verifyRuntime();
    BrowserStore.View view = store.read();
    assertThat(view.canonical()).isPositive();
    assertThat(view.applied()).isLessThanOrEqualTo(view.canonical());
    assertThat(view.snapshot().roadmaps()).hasSize(2);
    assertThat(view.snapshot().topic("java-start").id()).isEqualTo("TOP-JAVA-01");
  }

  @Test
  void abApplicationRoleCannotInsertUpdateOrDeleteProjection() throws Exception {
    assertAppMutationDenied("UPDATE content.browser_snapshot SET schema_version=1");
    assertAppMutationDenied("DELETE FROM content.browser_snapshot");
    assertAppMutationDenied(
        "INSERT INTO content.browser_snapshot("
            + "operation_id,revision,bundle_id,bundle_digest,schema_version,"
            + "snapshot_bytes,snapshot_sha256) "
            + "SELECT gen_random_uuid(),revision+1000,bundle_id,bundle_digest,1,'{}'::bytea,"
            + "encode(sha256('{}'::bytea),'hex') "
            + "FROM content.publication_canonical_head WHERE singleton");
    assertAppMutationDenied("DELETE FROM content.browser_canonical_snapshot");
    assertAppMutationDenied("UPDATE content.browser_canonical_snapshot SET revision=revision");
  }

  @Test
  void acMissingOrMismatchedCanonicalSnapshotFailsClosed() throws Exception {
    try (Connection connection = migrator.getConnection()) {
      connection.setAutoCommit(false);
      connection
          .createStatement()
          .execute(
              "ALTER TABLE content.publication_canonical_head DISABLE TRIGGER "
                  + "browser_snapshot_canonical_binding");
      connection
          .createStatement()
          .executeUpdate(
              "UPDATE content.publication_canonical_head "
                  + "SET revision=revision+1,bundle_id='missing-browser-snapshot' "
                  + "WHERE singleton");

      assertThatThrownBy(() -> new BrowserStore(app).read(connection))
          .isInstanceOf(WireCodec.Denied.class)
          .hasMessage("BROWSER_SNAPSHOT_MISSING");
      connection.rollback();
    }
  }

  @Test
  void adDatabaseRejectsCorruptDigestAndWrongBundleBinding() throws Exception {
    BrowserPublicationFixture.Prepared fixture =
        BrowserPublicationFixture.prepare(migrator.getConnection(), snapshot, "r07-corrupt");
    try (Connection connection = migrator.getConnection()) {
      connection.setAutoCommit(false);
      byte[] changed = "{}".getBytes();
      assertThatThrownBy(
              () ->
                  insertSnapshot(
                      connection,
                      fixture.operationId(),
                      fixture.targetRevision(),
                      fixture.bundleId(),
                      fixture.bundleDigest(),
                      changed,
                      PublicationDigest.sha256(changed)))
          .isInstanceOf(java.sql.SQLException.class);
      connection.rollback();
    }

    try (Connection connection = migrator.getConnection()) {
      connection.setAutoCommit(false);
      assertThatThrownBy(
              () ->
                  insertSnapshot(
                      connection,
                      fixture.operationId(),
                      fixture.targetRevision(),
                      fixture.bundleId(),
                      "f".repeat(64),
                      snapshot,
                      PublicationDigest.sha256(snapshot)))
          .isInstanceOf(java.sql.SQLException.class);
      connection.rollback();
    }
    try (Connection connection = migrator.getConnection();
        var statement =
            connection.prepareStatement(
                "UPDATE content.publication_operation SET state='ABORTED' WHERE operation_id=?")) {
      statement.setObject(1, fixture.operationId());
      statement.executeUpdate();
    }
  }

  @Test
  void aeCanonicalRequiresCommittedSnapshotAndRejectsPostCanonicalInstall() throws Exception {
    BrowserPublicationFixture.Prepared missing =
        BrowserPublicationFixture.prepare(migrator.getConnection(), snapshot, "r07-missing");
    try (Connection connection = migrator.getConnection()) {
      assertThatThrownBy(() -> BrowserPublicationFixture.fence(connection, missing))
          .isInstanceOf(java.sql.SQLException.class)
          .hasMessageContaining("BROWSER_SNAPSHOT_CANONICAL_BINDING_REQUIRED");
      connection.rollback();
    }
    try (Connection connection = migrator.getConnection()) {
      try (var statement =
          connection.prepareStatement(
              "UPDATE content.publication_operation SET state='ABORTED' WHERE operation_id=?")) {
        statement.setObject(1, missing.operationId());
        statement.executeUpdate();
      }
      BrowserPublicationFixture.Prepared retry =
          BrowserPublicationFixture.publish(connection, snapshot, "r07-retry");
      assertThat(retry.targetRevision()).isEqualTo(missing.targetRevision());
      try (var statement =
          connection.prepareStatement(
              "UPDATE content.publication_operation "
                  + "SET state='FENCE_PENDING' WHERE operation_id=?")) {
        statement.setObject(1, missing.operationId());
        statement.executeUpdate();
      }
      assertThatThrownBy(
              () ->
                  insertSnapshot(
                      connection,
                      missing.operationId(),
                      missing.targetRevision(),
                      missing.bundleId(),
                      missing.bundleDigest(),
                      snapshot,
                      missing.snapshotDigest()))
          .isInstanceOf(java.sql.SQLException.class);
      connection.rollback();
    }
  }

  @Test
  void azTwoConnectionsKeepHeadAndSnapshotFromOneMvccView() throws Exception {
    BrowserStore store = new BrowserStore(app);
    try (Connection reader = app.getConnection()) {
      reader.setAutoCommit(false);
      reader.setReadOnly(true);
      reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      BrowserStore.View before = store.read(reader);
      long nextRevision = before.canonical() + 1;

      try (Connection writer = migrator.getConnection()) {
        writer.setAutoCommit(false);
        BrowserPublicationFixture.Prepared published =
            BrowserPublicationFixture.publish(writer, snapshot, "r07-race");
        assertThat(published.targetRevision()).isEqualTo(nextRevision);
      }

      // Reader đã mở snapshot trước khi writer commit nên cả head và DTO vẫn
      // phải ở cùng revision cũ; không được ghép head mới với bytes cũ.
      BrowserStore.View during = store.read(reader);
      assertThat(during.canonical()).isEqualTo(before.canonical());
      assertThat(during.snapshot().topic("java-start").id()).isEqualTo("TOP-JAVA-01");
      reader.commit();

      BrowserStore.View after = store.read();
      assertThat(after.canonical()).isEqualTo(nextRevision);
      assertThat(after.applied()).isEqualTo(before.applied());
    }
  }

  private static PGSimpleDataSource source(Properties lab, String kind) {
    var source = new PGSimpleDataSource();
    source.setServerNames(new String[] {"127.0.0.1"});
    source.setPortNumbers(new int[] {Integer.parseInt(lab.getProperty("lab.port"))});
    source.setDatabaseName("content_db");
    source.setUser(lab.getProperty("content." + kind + ".user"));
    source.setPassword(lab.getProperty("content." + kind + ".password"));
    source.setConnectTimeout(1);
    source.setSocketTimeout(8);
    return source;
  }

  private static void assertAppMutationDenied(String sql) throws Exception {
    try (Connection connection = app.getConnection()) {
      assertThatThrownBy(() -> connection.createStatement().execute(sql))
          .isInstanceOf(java.sql.SQLException.class);
    }
  }

  private static void insertSnapshot(
      Connection connection,
      UUID operationId,
      long revision,
      String bundleId,
      String bundleDigest,
      byte[] bytes,
      String snapshotDigest)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.browser_snapshot("
                + "operation_id,revision,bundle_id,bundle_digest,schema_version,"
                + "snapshot_bytes,snapshot_sha256) VALUES (?,?,?,?,1,?,?)")) {
      statement.setObject(1, operationId);
      statement.setLong(2, revision);
      statement.setString(3, bundleId);
      statement.setString(4, bundleDigest);
      statement.setBytes(5, bytes);
      statement.setString(6, snapshotDigest);
      statement.executeUpdate();
    }
  }

  private static void seed() throws Exception {
    try (Connection connection = migrator.getConnection()) {
      BrowserPublicationFixture.ensureBaseline(
          connection,
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID(),
          "browser-fixture-capability-32-bytes-minimum".getBytes());
      BrowserPublicationFixture.publish(connection, snapshot, "r07-browser-fixture");
    }
  }
}
