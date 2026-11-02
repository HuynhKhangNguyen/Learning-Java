package lab.backend.content.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import lab.backend.content.identityaccess.AdmissionWire;
import lab.backend.content.transport.CertificateIdentity;
import lab.backend.content.transport.TransportPeerClient;
import lab.backend.content.transport.TransportSettings;
import lab.backend.content.transport.WireCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Chạy packaged API-C01–03 trên actual TLS và PostgreSQL.
 *
 * <p>Identity là provider-contract fixture strict; input gồm public-edge mTLS và
 * own DB, output chứng minh wire/cache/privacy behavior. Canonical Identity
 * composition vẫn thuộc regression R04d riêng, không phải claim của fixture này.
 */
@Timeout(90)
class BrowserRuntimeIT {
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  private static final String CREDENTIAL = "a".repeat(43);
  private static final UUID CONTEXT = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static Properties lab = new Properties();
  private static Path trustFile;
  private static Path config;
  private static Path artifact;
  private static Path log;
  private static TransportSettings settings;
  private static PGSimpleDataSource migrator;
  private static HttpsServer identity;
  private static ExecutorService threads;
  private static Process process;
  private static volatile boolean unavailable;

  @BeforeAll
  static void setup() throws Exception {
    String labInput = System.getProperty("lab.config");
    String trustInput = System.getProperty("trust.config");
    if (labInput == null
        || trustInput == null
        || labInput.startsWith("${")
        || trustInput.startsWith("${")) {
      throw new IllegalStateException("Actual owned DB/TLS is required");
    }
    try (var reader = Files.newBufferedReader(Path.of(labInput))) {
      lab.load(reader);
    }
    trustFile = Path.of(trustInput).toRealPath();
    settings = TransportSettings.load(environment());
    migrator = source("migrator");
    Flyway.configure()
        .dataSource(migrator)
        .schemas("migration_metadata")
        .defaultSchema("migration_metadata")
        .createSchemas(false)
        .cleanDisabled(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    provisionAdmission();
    seedBrowser();
    startIdentity();

    Properties options = new Properties();
    options.putAll(
        Map.of(
            "schemaVersion", "1",
            "enabled", "true",
            "realm", settings.realm(),
            "generation", settings.generation(),
            "topologyVersion", AdmissionWire.TOPOLOGY,
            "dbRunId", settings.dbRunId(),
            "identityPort", Integer.toString(identity.getAddress().getPort())));
    config = settings.directory().resolve("browser-admission.properties");
    try (var writer = Files.newBufferedWriter(config)) {
      options.store(writer, "owned browser fixture");
    }
    artifact = Path.of("target/content-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
    assertThat(artifact).isRegularFile();
    log = settings.directory().resolve("browser-runtime.log");
    startProcess();
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (process != null) {
      process.destroy();
      if (!process.waitFor(8, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      }
    }
    if (identity != null) {
      identity.stop(0);
    }
    if (threads != null) {
      threads.shutdownNow();
      assertThat(threads.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  @Tag("SEC-28")
  void anonymousCatalogAndOutlineAreRevisionBoundAndPrivateFree() throws Exception {
    HttpResponse<byte[]> catalog = call("/api/v1/catalog", Map.of());
    assertThat(catalog.statusCode()).isEqualTo(200);
    assertThat(catalog.headers().firstValue("Cache-Control"))
        .hasValueSatisfying(value -> assertThat(value).contains("public"));
    String body = new String(catalog.body());
    assertThat(body).contains("JAVA_BACKEND", "DEVOPS", "canonicalRevision", "learningSyncPending")
        .contains("TOP-JAVA-01", "CAP-JAVA-01", "TOP-OPS-01", "CAP-OPS-01", "PLANNED")
        .doesNotContain("expected", "rubric", "hint", "solution", CREDENTIAL, USER.toString());

    HttpResponse<byte[]> planned = call("/api/v1/topics/TOP-OPS-01", Map.of());
    assertThat(planned.statusCode()).isEqualTo(200);
    assertThat(new String(planned.body())).contains("PLANNED").doesNotContain("blocks");
  }

  @Test
  @Tag("SEC-16")
  void protectedBodyUsesFreshAdmissionAndPrivateNoStore() throws Exception {
    HttpResponse<byte[]> response =
        call(
            "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01",
            Map.of(
                "Cookie", "__Host-backend-lab-session=" + CREDENTIAL,
                "X-Session-Context", CONTEXT.toString()));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(new String(response.body()))
        .contains("UNIT-JAVA-01", "Java uses static types.")
        .doesNotContain(USER.toString(), CREDENTIAL, "admission", "principal", "expected");

    unavailable = true;
    try {
      HttpResponse<byte[]> denied =
          call(
              "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01",
              Map.of(
                  "Cookie", "__Host-backend-lab-session=" + CREDENTIAL,
                  "X-Session-Context", CONTEXT.toString()));
      assertThat(denied.statusCode()).isEqualTo(503);
      // Qualified transport chuẩn hóa upstream 503 đã authenticated thành
      // bounded PEER_DENIED; tuyệt đối không reuse ALLOW/body trước đó.
      assertThat(new String(denied.body()))
          .contains("PEER_DENIED")
          .doesNotContain("Java uses static types.");
    } finally {
      unavailable = false;
    }
  }

  @Test
  @Tag("SEC-25")
  void ambiguousRoutesAndForgedBrowserAuthorityFailBeforeBody() throws Exception {
    for (String path : new String[] {"/api/v1/catalog?owner=x", "/api/v1/topics/TOP%2dJAVA-01"}) {
      HttpResponse<byte[]> response = call(path, Map.of());
      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(new String(response.body())).doesNotContain("Java uses static types.", "expected");
    }
    HttpResponse<byte[]> response =
        call(
            "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01",
            Map.of("X-User-Id", USER.toString()));
    assertThat(response.statusCode()).isEqualTo(403);

    String chunked = rawChunkedCatalog();
    assertThat(chunked).contains(" 400 ", "BAD_CONTENT_TYPE");
    assertThat(chunked).doesNotContain("Java uses static types.", "CAP-JAVA-01", "expected");
  }

  private static String rawChunkedCatalog() throws Exception {
    SSLContext tls =
        TransportPeerClient.tls(settings, settings.directory().resolve("edge.p12"), "content");
    try (SSLSocket socket =
        (SSLSocket) tls.getSocketFactory().createSocket("127.0.0.1", 8080)) {
      socket.setSoTimeout(5_000);
      socket.startHandshake();
      String request =
          "GET /api/v1/catalog HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "Transfer-Encoding: chunked\r\n"
              + "Connection: close\r\n\r\n"
              + "1\r\nx\r\n0\r\n\r\n";
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void startIdentity() throws Exception {
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(
        settings.store(settings.directory().resolve("identity.p12")),
        settings.storePassword().toCharArray());
    var trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trusts.init(settings.store(settings.trustStore()));
    SSLContext tls = SSLContext.getInstance("TLS");
    tls.init(keys.getKeyManagers(), trusts.getTrustManagers(), null);
    identity = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    identity.setHttpsConfigurator(
        new HttpsConfigurator(tls) {
          @Override
          public void configure(HttpsParameters parameters) {
            SSLParameters ssl = tls.getDefaultSSLParameters();
            ssl.setNeedClientAuth(true);
            parameters.setSSLParameters(ssl);
          }
        });
    threads = Executors.newCachedThreadPool();
    identity.setExecutor(threads);
    identity.createContext("/internal/v1/auth-context/admissions", BrowserRuntimeIT::admit);
    identity.start();
  }

  private static void admit(HttpExchange exchange) throws java.io.IOException {
    try {
      var certificate =
          (java.security.cert.X509Certificate)
              ((com.sun.net.httpserver.HttpsExchange) exchange)
                  .getSSLSession()
                  .getPeerCertificates()[0];
      if (!"content".equals(CertificateIdentity.identity(certificate, "lab", true))
          || unavailable) {
        exchange.sendResponseHeaders(503, -1);
        return;
      }
      var request = WireCodec.strict(exchange.getRequestBody().readNBytes(8193));
      Instant now = Instant.now();
      Map<String, Object> response = new java.util.LinkedHashMap<>();
      response.put("schemaVersion", 1);
      response.put("decision", "ALLOW");
      response.put("admissionId", UUID.randomUUID().toString());
      response.put("requestNonce", request.path("requestNonce").asString());
      response.put("provider", "identity");
      response.put("caller", "content");
      response.put("audience", "content");
      response.put("authRealmId", settings.realm());
      response.put("userId", USER.toString());
      response.put("sessionRef", UUID.randomUUID().toString());
      response.put("sessionContextId", CONTEXT.toString());
      response.put("credentialEpoch", "1");
      response.put("action", AdmissionWire.ACTION);
      response.put("requestBinding", request.path("requestBinding").asString());
      response.put("issuedAt", TIME.format(now));
      response.put("serverTime", TIME.format(now));
      response.put("validUntil", TIME.format(now.plusSeconds(14)));
      response.put("idleDeadline", TIME.format(now.plusSeconds(120)));
      response.put("absoluteDeadline", TIME.format(now.plusSeconds(300)));
      response.put("csrfVerified", false);
      byte[] bytes = WireCodec.JSON.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    } catch (Exception failure) {
      exchange.sendResponseHeaders(500, -1);
    } finally {
      exchange.close();
    }
  }

  private static void startProcess() throws Exception {
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-jar",
            artifact.toString(),
            "--spring.profiles.active=browser",
            "--backendlab.browser.enabled=true",
            "--backendlab.trust.config=" + trustFile,
            "--backendlab.admission.config=" + config,
            "--logging.level.root=ERROR");
    builder.directory(settings.directory().toFile());
    builder
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
    process = builder.start();
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException("Browser runtime exited; inspect retained log");
      }
      try {
        if (call("/api/v1/catalog", Map.of()).statusCode() == 200) {
          return;
        }
      } catch (Exception expected) {
        // Listener vẫn đang khởi động; chỉ retry trong deadline bounded của fixture.
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Browser runtime startup timeout");
  }

  private static HttpResponse<byte[]> call(
      String path, Map<String, String> headers) throws Exception {
    try (var client =
        HttpClient.newBuilder()
            .sslContext(
                TransportPeerClient.tls(
                    settings, settings.directory().resolve("edge.p12"), "content"))
            .connectTimeout(Duration.ofSeconds(1))
            .build()) {
      var request = HttpRequest.newBuilder(URI.create("https://127.0.0.1:8080" + path)).GET();
      headers.forEach(request::header);
      return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  private static StandardEnvironment environment() {
    var environment = new StandardEnvironment();
    environment.setActiveProfiles("browser");
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "fixture", Map.of("backendlab.trust.config", trustFile.toString())));
    return environment;
  }

  private static PGSimpleDataSource source(String kind) {
    var source = new PGSimpleDataSource();
    source.setServerNames(new String[] {"127.0.0.1"});
    source.setPortNumbers(new int[] {Integer.parseInt(lab.getProperty("lab.port"))});
    source.setDatabaseName("content_db");
    source.setUser(lab.getProperty("content." + kind + ".user"));
    source.setPassword(lab.getProperty("content." + kind + ".password"));
    return source;
  }

  private static void provisionAdmission() throws Exception {
    Path root =
        Path.of(
                WireCodec.JSON
                    .readTree(Files.readAllBytes(trustFile.getParent().resolve("ownership.json")))
                    .path("workspace")
                    .asString())
            .toRealPath();
    try (Connection connection = migrator.getConnection();
        var settingsStatement =
            connection.prepareStatement(
                "SELECT set_config('backend_lab.provision_realm',?,false),"
                    + "set_config('backend_lab.provision_generation',?,false),"
                    + "set_config('backend_lab.provision_run_id',?,false)")) {
      settingsStatement.setString(1, settings.realm());
      settingsStatement.setString(2, settings.generation());
      settingsStatement.setString(3, lab.getProperty("lab.runId"));
      settingsStatement.execute();
      connection
          .createStatement()
          .execute(
              Files.readString(
                  root.resolve("ops/content/provision-admission-realm.sql")));
    }
  }

  private static void seedBrowser() throws Exception {
    byte[] snapshot =
        Files.readAllBytes(
            Path.of(
                BrowserRuntimeIT.class
                    .getResource("/contracts/content/public/fixtures/browser-snapshot-v1.json")
                    .toURI()));
    try (Connection connection = migrator.getConnection()) {
      BrowserPublicationFixture.ensureBaseline(
          connection,
          UUID.fromString(settings.realm()),
          UUID.fromString(settings.generation()),
          UUID.randomUUID(),
          "browser-runtime-capability-32-bytes-minimum".getBytes());
      BrowserPublicationFixture.publish(connection, snapshot, "r07-browser-runtime");
    }
  }
}
