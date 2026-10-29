package lab.backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import lab.backend.content.transport.TransportPeerClient;
import lab.backend.content.transport.TransportSettings;
import lab.backend.content.transport.WireCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.node.ObjectNode;

/** Packaged INT-03 trên actual mTLS + own PostgreSQL; không activate canonical revision. */
@Timeout(90)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublicationRuntimeIT {
  private static final int MAX_MANIFEST_BYTES = 262_144;
  private static TransportSettings settings;
  private static Properties database;
  private static Process process;
  private static Path log;
  private static Path artifact;
  private static Path candidatePath;
  private static Path bundlePath;
  private static byte[] bundle;
  private static byte[] manifest;
  private static byte[] boundaryManifest;
  private static String digest;
  private static String manifestDigest;

  @BeforeAll
  static void startPackagedProvider() throws Exception {
    String trustInput = System.getProperty("trust.config");
    String labInput = System.getProperty("lab.config");
    if (trustInput == null || labInput == null
        || trustInput.startsWith("${") || labInput.startsWith("${")) {
      throw new IllegalStateException("R06a managed trust/DB config required");
    }
    database = new Properties();
    try (Reader reader = Files.newBufferedReader(Path.of(labInput).toRealPath(), StandardCharsets.UTF_8)) {
      database.load(reader);
    }
    PGSimpleDataSource migrator = source(database, "migrator");
    Flyway.configure().dataSource(migrator).defaultSchema("migration_metadata")
        .schemas("migration_metadata").createSchemas(false).cleanDisabled(true)
        .locations("classpath:db/migration").load().migrate();
    try (Connection connection = migrator.getConnection(); var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO content.publication_canonical_head(revision,bundle_id,bundle_digest) "
              + "VALUES(1,'fx05-p1',repeat('0',64))");
    }

    Path fixtures = Path.of("../../../contracts/fixtures/publication").toRealPath();
    candidatePath = fixtures.resolve("fx05-candidate-cosmetic.json");
    bundlePath = fixtures.resolve("fx05-private-bundle.json");
    byte[] candidate = Files.readAllBytes(candidatePath);
    bundle = Files.readAllBytes(bundlePath);
    CandidateValidator.Valid valid = new CandidateValidator().validate(candidate, bundle);
    digest = valid.bundleDigest();
    manifest = valid.manifestBytes();
    manifestDigest = PublicationDigest.sha256(manifest);
    new PublicationStore(source(database, "app")).prepare(valid);
    seedProviderBoundaryRows(migrator);

    var environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource(
        "fixture", Map.of("backendlab.trust.config", Path.of(trustInput).toRealPath().toString())));
    settings = TransportSettings.load(environment);
    artifact = Path.of("target/content-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
    assertThat(artifact).isRegularFile();
    Path working = Files.createTempDirectory(settings.directory(), "publication-artifact-");
    log = working.resolve("runtime.log");
    process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
        "-jar", artifact.toString(),
        "--spring.profiles.active=publication",
        "--backendlab.trust.config=" + Path.of(trustInput).toRealPath(),
        "--logging.level.root=ERROR")
        .directory(working.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) throw new IllegalStateException("Packaged publication provider exited");
      try {
        if (call("learning", 8443, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of())
            .statusCode() == 200) return;
      } catch (Exception startup) {
        // Listener và TLS material có thể chưa sẵn sàng; bounded deadline ở trên.
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Packaged publication provider not ready");
  }

  @AfterAll
  static void stopOwnedProvider() throws Exception {
    if (process != null) {
      process.destroy();
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      }
    }
    for (int port : new int[] {8080, 8443}) {
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      boolean closed = false;
      while (System.nanoTime() < deadline) {
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
        } catch (java.io.IOException expected) {
          closed = true;
          break;
        }
        Thread.sleep(50);
      }
      assertThat(closed).as("owned listener closed on " + port).isTrue();
    }
    if (log != null && Files.exists(log)) {
      assertThat(Files.readString(log)).doesNotContain(
          "PRIVATE_EXPECTED_MUST_NOT_REACH_PUBLIC_OUTLINE", "SQLException", "password");
    }
  }

  @Test
  @Order(1)
  void exactBundleAndManifestResourcesReturnStoredBytesAndBoundedMetadata() throws Exception {
    HttpResponse<byte[]> bundleResponse = call(
        "learning", 8443, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of());
    assertThat(bundleResponse.statusCode()).isEqualTo(200);
    assertThat(bundleResponse.body()).containsExactly(bundle);
    assertThat(bundleResponse.headers().firstValue("X-Content-Contract-Version")).hasValue("1");
    assertThat(bundleResponse.headers().firstValue("X-Content-Revision")).hasValue("2");
    assertThat(bundleResponse.headers().firstValue("Digest")).hasValue("sha-256=" + digest);
    assertThat(bundleResponse.headers().firstValue("X-Content-Manifest-Digest"))
        .hasValue("sha-256=" + manifestDigest);
    assertThat(bundleResponse.headers().firstValue("X-Content-Manifest-Length"))
        .hasValue(Integer.toString(manifest.length));
    assertThat(bundleResponse.headers().firstValue("X-Content-Manifest")).isEmpty();
    assertThat(bundleResponse.headers().firstValue("Link"))
        .hasValue("</internal/v1/content-bundles/fx05-p2/manifest>; rel=\"manifest\"; type=\"application/json\"");
    assertThat(bundleResponse.headers().allValues("Set-Cookie")).isEmpty();

    HttpResponse<byte[]> manifestResponse = call(
        "learning", 8443, "/internal/v1/content-bundles/fx05-p2/manifest", "GET", Map.of());
    assertThat(manifestResponse.statusCode()).isEqualTo(200);
    assertThat(manifestResponse.body()).containsExactly(manifest);
    assertThat(manifestResponse.headers().firstValue("Digest"))
        .hasValue("sha-256=" + manifestDigest);
    assertThat(manifestResponse.headers().firstValue("X-Content-Bundle-Digest"))
        .hasValue("sha-256=" + digest);
  }

  @Test
  @Order(2)
  void largestValidManifestUsesBodyResourceAndOversizeIsRejected() throws Exception {
    HttpResponse<byte[]> bundleResponse = call(
        "learning", 8443, "/internal/v1/content-bundles/fx05-boundary", "GET", Map.of());
    assertThat(bundleResponse.statusCode()).isEqualTo(200);
    assertThat(bundleResponse.headers().firstValue("X-Content-Manifest-Length"))
        .hasValue(Integer.toString(MAX_MANIFEST_BYTES));
    assertThat(bundleResponse.headers().firstValue("X-Content-Manifest")).isEmpty();
    HttpResponse<byte[]> manifestResponse = call(
        "learning", 8443, "/internal/v1/content-bundles/fx05-boundary/manifest", "GET", Map.of());
    assertThat(manifestResponse.statusCode()).isEqualTo(200);
    assertThat(manifestResponse.body()).containsExactly(boundaryManifest);

    byte[] oversized = Arrays.copyOf(boundaryManifest, MAX_MANIFEST_BYTES + 1);
    oversized[oversized.length - 1] = ']';
    try (Connection connection = source(database, "migrator").getConnection()) {
      assertThatThrownBy(() -> insertBundle(
          connection, "fx05-oversized", "{}".getBytes(StandardCharsets.UTF_8), oversized, true))
          .isInstanceOf(SQLException.class)
          .extracting(failure -> ((SQLException) failure).getSQLState())
          .isEqualTo("23514");
      assertThat(count(connection,
          "SELECT count(*) FROM content.publication_bundle WHERE bundle_id='fx05-oversized'"))
          .isZero();
    }
  }

  @Test
  @Order(3)
  void unknownUnvalidatedAndCorruptStoredRowsFailClosed() throws Exception {
    denied(call("learning", 8443, "/internal/v1/content-bundles/missing", "GET", Map.of()), 404);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-unvalidated", "GET", Map.of()), 404);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-corrupt-bundle", "GET", Map.of()), 503);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-corrupt-manifest/manifest", "GET", Map.of()), 503);
  }

  @Test
  @Order(4)
  void publicWrongCallerHeadersMethodQueryAndAmbiguousPathFailClosed() throws Exception {
    denied(call("edge", 8080, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of()), 404);
    for (String caller : List.of("identity", "content", "operator")) {
      denied(call(caller, 8443, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of()), 403);
    }
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-p2", "POST", Map.of()), 405);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-p2?x=1", "GET", Map.of()), 400);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-p2/manifest/", "GET", Map.of()), 404);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of("X-Caller", "learning")), 403);
    denied(call("learning", 8443, "/internal/v1/content-bundles/fx05-p2", "GET", Map.of("Origin", "https://attacker.invalid")), 403);
  }

  @Test
  @Order(5)
  void wrongCaEkuSanEnvironmentAndNoCertificateCannotReachPayload() {
    for (String identity : List.of("wrong-ca", "wrong-eku", "expired", "wrong-san", "wrong-env", "ambiguous")) {
      cannotRead(identity, Map.of());
    }
    cannotRead(null, Map.of("Cookie", "session=x"));
  }

  @Test
  @Order(6)
  void packagedCliValidatesPreparesExactRetryAndRejectsChecksumMismatch() throws Exception {
    Process validate = cli("validate", candidatePath, bundlePath);
    assertThat(validate.exitValue()).isZero();
    assertThat(validate.inputReader().readLine()).startsWith("VALID bundle=fx05-p2 target=2 digest=");
    for (int restart = 0; restart < 2; restart++) {
      Process prepare = cli("prepare", candidatePath, bundlePath);
      assertThat(prepare.exitValue()).isZero();
      assertThat(prepare.inputReader().readLine()).startsWith("VALID bundle=fx05-p2 target=2 digest=");
    }

    ObjectNode changedBundle = PublicationFixtureSupport.bundleNode();
    ((ObjectNode) changedBundle.path("expected")).put("sentinel", "checksum-mismatch-private");
    Path wrongBytes = Files.createTempFile(settings.directory(), "checksum-mismatch-", ".json");
    Files.write(wrongBytes, WireCodec.JSON.writeValueAsBytes(changedBundle));
    assertRejected(cli("validate", candidatePath, wrongBytes), "checksum-mismatch-private");
  }

  @Test
  @Order(7)
  void packagedCliRejectsDatabaseFaultAndConflictingRetryAtomically() throws Exception {
    byte[] conflictingBytes = PublicationFixtureSupport.mutateAndBind(candidate ->
        ((ObjectNode) candidate.path("oldVersions").get(0)).put("reason", "Changed after approval"));
    Path conflictingCandidate = Files.createTempFile(settings.directory(), "conflicting-retry-", ".json");
    Files.write(conflictingCandidate, conflictingBytes);
    assertRejected(cli("prepare", conflictingCandidate, bundlePath), "Changed after approval");

    releaseReservation();
    CandidateValidator.Valid fault = PublicationFixtureSupport.variant(
        UUID.randomUUID(), "fx05-cli-db-fault", "cli-db-private", "CLI DB fault candidate");
    Path faultCandidate = Files.createTempFile(settings.directory(), "db-fault-candidate-", ".json");
    Path faultBundle = Files.createTempFile(settings.directory(), "db-fault-bundle-", ".json");
    Files.write(faultCandidate, fault.candidateBytes());
    Files.write(faultBundle, fault.bundleBytes());
    try (Connection connection = source(database, "migrator").getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate("CREATE FUNCTION content.inject_publication_cli_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'CLI_PUBLICATION_DB_FAULT' USING ERRCODE='40001'; END $$");
      statement.executeUpdate("CREATE TRIGGER publication_cli_fault BEFORE INSERT ON content.publication_candidate FOR EACH ROW EXECUTE FUNCTION content.inject_publication_cli_fault()");
    }
    try {
      assertRejected(cli("prepare", faultCandidate, faultBundle), "cli-db-private");
    } finally {
      try (Connection connection = source(database, "migrator").getConnection();
          var statement = connection.createStatement()) {
        statement.executeUpdate("DROP TRIGGER publication_cli_fault ON content.publication_candidate");
        statement.executeUpdate("DROP FUNCTION content.inject_publication_cli_fault()");
      }
    }
    try (Connection connection = source(database, "app").getConnection()) {
      assertThat(count(connection,
          "SELECT count(*) FROM content.publication_bundle WHERE bundle_id='fx05-cli-db-fault'"))
          .isZero();
      assertThat(count(connection,
          "SELECT count(*) FROM content.publication_candidate WHERE operation_id='" + fault.operationId() + "'"))
          .isZero();
      assertThat(queryText(connection,
          "SELECT operation_id::text FROM content.publication_candidate_reservation WHERE singleton"))
          .isNull();
      assertThat(count(connection,
          "SELECT revision FROM content.publication_canonical_head WHERE singleton")).isEqualTo(1);
    }
  }

  private static void seedProviderBoundaryRows(PGSimpleDataSource migrator) throws Exception {
    boundaryManifest = new byte[MAX_MANIFEST_BYTES];
    Arrays.fill(boundaryManifest, (byte) ' ');
    boundaryManifest[0] = '[';
    boundaryManifest[boundaryManifest.length - 1] = ']';
    byte[] smallBundle = "{}".getBytes(StandardCharsets.UTF_8);
    try (Connection connection = migrator.getConnection()) {
      insertBundle(connection, "fx05-boundary", smallBundle, boundaryManifest, true);
      insertBundle(connection, "fx05-unvalidated", smallBundle, "[]".getBytes(StandardCharsets.UTF_8), false);
      insertBundle(connection, "fx05-corrupt-bundle", smallBundle, "[]".getBytes(StandardCharsets.UTF_8), true);
      try (var statement = connection.prepareStatement(
          "UPDATE content.publication_bundle SET bundle_digest=repeat('f',64) WHERE bundle_id='fx05-corrupt-bundle'")) {
        // Migration owner phải tắt immutable trigger chỉ trong setup fixture; app
        // không có quyền này và runtime vẫn đọc row corrupt qua exact store path.
        try (var toggle = connection.createStatement()) {
          toggle.executeUpdate("ALTER TABLE content.publication_bundle DISABLE TRIGGER publication_bundle_immutable");
          statement.executeUpdate();
          toggle.executeUpdate("ALTER TABLE content.publication_bundle ENABLE TRIGGER publication_bundle_immutable");
        }
      }
      insertBundle(connection, "fx05-corrupt-manifest", smallBundle, "[]".getBytes(StandardCharsets.UTF_8), true);
      try (var toggle = connection.createStatement()) {
        toggle.executeUpdate("ALTER TABLE content.publication_bundle DISABLE TRIGGER publication_bundle_immutable");
        toggle.executeUpdate("UPDATE content.publication_bundle SET manifest_digest=repeat('e',64) WHERE bundle_id='fx05-corrupt-manifest'");
        toggle.executeUpdate("ALTER TABLE content.publication_bundle ENABLE TRIGGER publication_bundle_immutable");
      }
    }
  }

  private static void insertBundle(
      Connection connection, String id, byte[] bytes, byte[] manifestBytes, boolean validated)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "INSERT INTO content.publication_bundle("
            + "bundle_id,target_revision,bundle_bytes,bundle_digest,manifest_bytes,manifest_digest,validated) "
            + "VALUES(?,2,?,?,?,?,?)")) {
      statement.setString(1, id);
      statement.setBytes(2, bytes);
      statement.setString(3, PublicationDigest.sha256(bytes));
      statement.setBytes(4, manifestBytes);
      statement.setString(5, PublicationDigest.sha256(manifestBytes));
      statement.setBoolean(6, validated);
      statement.executeUpdate();
    }
  }

  private static void releaseReservation() throws SQLException {
    try (Connection connection = source(database, "migrator").getConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE content.publication_candidate_reservation "
              + "SET generation=generation+1,operation_id=NULL WHERE singleton");
    }
  }

  private static PGSimpleDataSource source(Properties properties, String kind) {
    var dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {"127.0.0.1"});
    dataSource.setPortNumbers(new int[] {Integer.parseInt(properties.getProperty("lab.port"))});
    dataSource.setDatabaseName("content_db");
    dataSource.setUser(properties.getProperty("content." + kind + ".user"));
    dataSource.setPassword(properties.getProperty("content." + kind + ".password"));
    dataSource.setSslMode("disable");
    return dataSource;
  }

  private static HttpResponse<byte[]> call(
      String identity, int port, String path, String method, Map<String, String> headers)
      throws Exception {
    Path store = identity == null ? null : settings.directory().resolve(identity + ".p12");
    SSLContext ssl = TransportPeerClient.tls(settings, store, "content");
    try (HttpClient client = HttpClient.newBuilder().sslContext(ssl)
        .connectTimeout(Duration.ofSeconds(2)).build()) {
      var builder = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + path))
          .timeout(Duration.ofSeconds(5));
      headers.forEach(builder::header);
      return client.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
          HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  private static void cannotRead(String identity, Map<String, String> headers) {
    try {
      var response = call(identity, 8443, "/internal/v1/content-bundles/fx05-p2", "GET", headers);
      assertThat(response.statusCode()).isNotEqualTo(200);
      assertThat(response.body()).isNotEqualTo(bundle);
      assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain(
          "PRIVATE_EXPECTED_MUST_NOT_REACH_PUBLIC_OUTLINE", "CASE-CATALOG-FIXTURE");
    } catch (java.io.IOException denied) {
      // TLS handshake bị từ chối trước HTTP cũng là expected.
    } catch (Exception unexpected) {
      throw new AssertionError(unexpected);
    }
  }

  private static Process cli(String action, Path candidate, Path privateBundle) throws Exception {
    Process process = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
        "-Dloader.main=lab.backend.content.publication.PublicationLabCli",
        "-Dbackendlab.trust.config=" + settings.directory().resolve("content.properties"),
        "-cp", artifact.toString(),
        "org.springframework.boot.loader.launch.PropertiesLauncher",
        action, candidate.toString(), privateBundle.toString())
        .directory(settings.directory().toFile()).start();
    assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
    return process;
  }

  private static void assertRejected(Process rejected, String privateMarker) throws Exception {
    assertThat(rejected.exitValue()).isNotZero();
    String stdout = new String(rejected.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(rejected.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(stderr).contains("PUBLICATION_CANDIDATE_REJECTED")
        .doesNotContain(privateMarker, "SQLException", "password");
    assertThat(stdout).doesNotContain(privateMarker, "SQLException", "password");
  }

  private static void denied(HttpResponse<byte[]> response, int status) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain(
        "PRIVATE_EXPECTED_MUST_NOT_REACH_PUBLIC_OUTLINE", "expected", "SQLException", "password");
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
  }

  private static int count(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
      result.next();
      return result.getInt(1);
    }
  }

  private static String queryText(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }
}
