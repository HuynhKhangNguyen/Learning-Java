package lab.backend.learning.database;

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
import java.time.Duration;
import java.util.Arrays;
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

/** Real DB fixtures only; no learner/auth/work algorithm or simulated PostgreSQL. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(30)
class DatabaseIsolationIT {
  private static final String SERVICE = "learning";
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
    assertThat(initialMigrations).isEqualTo(6); // năm production migrations và transaction fixture riêng của test
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
  @ValueSource(strings = {"CREATE TABLE learning.forbidden(id integer)", "DROP TABLE learning.service_descriptor", "CREATE EXTENSION dblink"})
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
