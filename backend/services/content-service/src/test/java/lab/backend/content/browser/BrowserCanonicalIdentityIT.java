package lab.backend.content.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lab.backend.content.identityaccess.AdmissionWire;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Chứng minh API-C03 qua packaged canonical Identity/Content và own PostgreSQL/mTLS.
 *
 * <p>Input đi qua login/session API thật; output kiểm body/no-store, restart và
 * logout-fence race. Test không import Identity domain hoặc ghi credential/session
 * row trực tiếp; mọi lỗi admission phải deny trước public unit body.
 */
@Timeout(240)
class BrowserCanonicalIdentityIT {
  private static final List<String> SERVICES = List.of("identity", "content", "learning");
  private static final String COOKIE = "__Host-backend-lab-session";
  private static final String ORIGIN = "https://localhost:17441";
  private static final String PASSWORD = "r07 canonical browser password 🔐";
  private static final int IDENTITY_PUBLIC = 18081;
  private static final int IDENTITY_PRIVATE = 18441;
  private static final int CONTENT_PUBLIC = 18082;
  private static final int CONTENT_PRIVATE = 18442;
  private static final Properties LAB = new Properties();
  private static final Map<String, Properties> TRUST = new LinkedHashMap<>();
  private static final Map<String, Properties> ORIGINAL_TRUST = new LinkedHashMap<>();

  private static Path root;
  private static Path directory;
  private static Path contentTrust;
  private static Path identityCanonical;
  private static Path contentAdmission;
  private static Path corpus;
  private static Path identityArtifact;
  private static Path contentArtifact;
  private static String corpusHash;
  private static String throttleKey;
  private static TransportSettings settings;
  private static PGSimpleDataSource contentMigrator;
  private static PGSimpleDataSource identityMigrator;
  private static Process identityProcess;
  private static Process contentProcess;
  private static Login login;

  record Login(String cookie, JsonNode view) {}

  @BeforeAll
  static void setup() throws Exception {
    String databaseInput = System.getProperty("lab.config");
    String trustInput = System.getProperty("trust.config");
    if (databaseInput == null
        || trustInput == null
        || databaseInput.startsWith("${")
        || trustInput.startsWith("${")) {
      throw new IllegalStateException("Actual owned DB/TLS is required");
    }
    try (var reader = Files.newBufferedReader(Path.of(databaseInput))) {
      LAB.load(reader);
    }
    contentTrust = Path.of(trustInput).toRealPath();
    directory = contentTrust.getParent();
    JsonNode marker = WireCodec.strict(Files.readAllBytes(directory.resolve("ownership.json")));
    root = Path.of(marker.path("workspace").asString()).toRealPath();
    prepareCanonicalTrust();

    settings = TransportSettings.load(contentEnvironment());
    contentMigrator = source("content", "migrator");
    identityMigrator = source("identity", "migrator");
    migrateContent();
    migrateIdentity();
    provisionContent();
    provisionIdentity();
    seedBrowser();
    prepareCanonicalConfiguration();

    identityArtifact =
        root.resolve(
            "backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar");
    contentArtifact = Path.of("target/content-service-0.1.0-SNAPSHOT.jar").toAbsolutePath();
    assertThat(identityArtifact).isRegularFile();
    assertThat(contentArtifact).isRegularFile();

    String account = createAccount();
    startIdentity();
    login = login(account);
    startContent();
  }

  @AfterAll
  static void cleanup() throws Exception {
    Exception failure = null;
    try {
      stopContent();
    } catch (Exception error) {
      failure = error;
    }
    try {
      stopIdentity();
    } catch (Exception error) {
      if (failure == null) {
        failure = error;
      } else {
        failure.addSuppressed(error);
      }
    }
    for (int port :
        new int[] {IDENTITY_PUBLIC, IDENTITY_PRIVATE, CONTENT_PUBLIC, CONTENT_PRIVATE}) {
      try {
        closed(port);
      } catch (Exception error) {
        if (failure == null) {
          failure = error;
        } else {
          failure.addSuppressed(error);
        }
      }
    }
    try {
      restoreFoundationTrust();
    } catch (Exception error) {
      if (failure == null) {
        failure = error;
      } else {
        failure.addSuppressed(error);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Test
  @Tag("SEC-16")
  void actualIdentityProtectsBodyAcrossRestartAndLogoutFenceRace() throws Exception {
    HttpResponse<byte[]> first = contentUnit(login);
    assertUnit(first);

    HttpResponse<byte[]> wrongContext =
        contentUnit(new Login(login.cookie(), withContext(login.view(), UUID.randomUUID())));
    assertThat(wrongContext.statusCode()).isNotEqualTo(200);
    assertThat(new String(wrongContext.body(), StandardCharsets.UTF_8))
        .doesNotContain("Java uses static types.");

    stopIdentity();
    HttpResponse<byte[]> unavailable = contentUnit(login);
    assertThat(unavailable.statusCode()).isEqualTo(503);
    assertThat(new String(unavailable.body(), StandardCharsets.UTF_8))
        .contains("IDENTITY_UNAVAILABLE")
        .doesNotContain("Java uses static types.");
    startIdentity();
    assertUnit(contentUnit(login));

    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var read =
          pool.submit(
              () -> {
                barrier.await();
                return contentUnit(login);
              });
      var logout =
          pool.submit(
              () -> {
                barrier.await();
                return logout(login);
              });
      HttpResponse<byte[]> raced = read.get(20, TimeUnit.SECONDS);
      assertThat(logout.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(202);
      assertThat(raced.statusCode()).isIn(200, 401, 403, 409, 503);
    }

    awaitContentFence(UUID.fromString(login.view().path("sessionContextId").asString()));
    HttpResponse<byte[]> fenced = contentUnit(login);
    assertThat(fenced.statusCode()).isNotEqualTo(200);
    assertThat(new String(fenced.body(), StandardCharsets.UTF_8))
        .doesNotContain("Java uses static types.", PASSWORD, login.cookie());
  }

  private static JsonNode withContext(JsonNode source, UUID context) throws Exception {
    var copy = (ObjectNode) source.deepCopy();
    copy.put("sessionContextId", context.toString());
    return copy;
  }

  private static void prepareCanonicalTrust() throws Exception {
    for (String service : SERVICES) {
      Path file = directory.resolve(service + ".properties");
      Properties properties = new Properties();
      try (var reader = Files.newBufferedReader(file)) {
        properties.load(reader);
      }
      if (!LAB.getProperty("lab.runId").equals(properties.getProperty("dbRunId"))) {
        throw new IllegalStateException("Trust/DB run mismatch");
      }
      Properties original = new Properties();
      original.putAll(properties);
      ORIGINAL_TRUST.put(service, original);
      properties.setProperty("schemaVersion", "2");
      properties.setProperty("listenerTopology", TransportSettings.CANONICAL);
      TRUST.put(service, properties);
      store(file, properties);
    }
  }

  private static void restoreFoundationTrust() throws Exception {
    for (var entry : ORIGINAL_TRUST.entrySet()) {
      store(directory.resolve(entry.getKey() + ".properties"), entry.getValue());
    }
  }

  private static StandardEnvironment contentEnvironment() {
    var environment = new StandardEnvironment();
    environment.setActiveProfiles("canonical");
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "r07-canonical",
                Map.of(
                    "server.port",
                    Integer.toString(CONTENT_PUBLIC),
                    "backendlab.trust.config",
                    contentTrust.toString())));
    return environment;
  }

  private static PGSimpleDataSource source(String service, String kind) {
    var source = new PGSimpleDataSource();
    source.setServerNames(new String[] {"127.0.0.1"});
    source.setPortNumbers(new int[] {Integer.parseInt(LAB.getProperty("lab.port"))});
    source.setDatabaseName(service + "_db");
    source.setUser(LAB.getProperty(service + "." + kind + ".user"));
    source.setPassword(LAB.getProperty(service + "." + kind + ".password"));
    source.setConnectTimeout(1);
    source.setSocketTimeout(8);
    source.setSslMode("disable");
    return source;
  }

  private static void migrateContent() {
    Flyway.configure()
        .dataSource(contentMigrator)
        .schemas("migration_metadata")
        .defaultSchema("migration_metadata")
        .createSchemas(false)
        .cleanDisabled(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  private static void migrateIdentity() {
    Path migrations =
        root.resolve("backend/services/identity-service/src/main/resources/db/migration");
    Flyway.configure()
        .dataSource(identityMigrator)
        .schemas("migration_metadata")
        .defaultSchema("migration_metadata")
        .createSchemas(false)
        .cleanDisabled(true)
        .locations("filesystem:" + migrations)
        .load()
        .migrate();
  }

  private static void provisionContent() throws Exception {
    try (Connection connection = contentMigrator.getConnection();
        var statement =
            connection.prepareStatement(
                "SELECT set_config('backend_lab.provision_realm',?,false),"
                    + "set_config('backend_lab.provision_generation',?,false),"
                    + "set_config('backend_lab.provision_run_id',?,false)")) {
      statement.setString(1, settings.realm());
      statement.setString(2, settings.generation());
      statement.setString(3, LAB.getProperty("lab.runId"));
      statement.execute();
      connection
          .createStatement()
          .execute(Files.readString(root.resolve("ops/content/provision-admission-realm.sql")));
    }
  }

  private static void provisionIdentity() throws Exception {
    try (Connection connection = identityMigrator.getConnection();
        var statement =
            connection.prepareStatement(
                "SELECT set_config('backend_lab.provision_realm',?,false),"
                    + "set_config('backend_lab.provision_generation',?,false),"
                    + "set_config('backend_lab.provision_run_id',?,false),"
                    + "set_config('backend_lab.provision_identity_generation',?,false),"
                    + "set_config('backend_lab.provision_content_generation',?,false),"
                    + "set_config('backend_lab.provision_learning_generation',?,false)")) {
      statement.setString(1, settings.realm());
      statement.setString(2, TRUST.get("identity").getProperty("generation"));
      statement.setString(3, LAB.getProperty("lab.runId"));
      for (int index = 0; index < SERVICES.size(); index++) {
        statement.setString(index + 4, TRUST.get(SERVICES.get(index)).getProperty("generation"));
      }
      statement.execute();
      try (var script = connection.createStatement()) {
        script.execute(Files.readString(root.resolve("ops/identity/provision-session-realm.sql")));
        script.execute(Files.readString(root.resolve("ops/identity/provision-delivery-realm.sql")));
      }
    }
  }

  private static void prepareCanonicalConfiguration() throws Exception {
    corpus = directory.resolve("r07-canonical-password-corpus.txt");
    Files.copy(
        root.resolve("ops/identity/password-corpus.txt"),
        corpus,
        StandardCopyOption.REPLACE_EXISTING);
    corpusHash =
        WireCodec.strict(
                Files.readAllBytes(root.resolve("ops/identity/password-corpus.json")))
            .path("sha256")
            .asString();
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    throttleKey = Base64.getEncoder().encodeToString(key);

    Properties canonical = new Properties();
    canonical.putAll(
        Map.ofEntries(
            Map.entry("schemaVersion", "1"),
            Map.entry("enabled", "true"),
            Map.entry("listenerTopology", TransportSettings.CANONICAL),
            Map.entry("realm", settings.realm()),
            Map.entry("generation", TRUST.get("identity").getProperty("generation")),
            Map.entry("dbRunId", LAB.getProperty("lab.runId")),
            Map.entry("origin", ORIGIN),
            Map.entry("corpusPath", corpus.toString()),
            Map.entry("corpusSha256", corpusHash),
            Map.entry("throttleKey", throttleKey)));
    for (String service : SERVICES) {
      canonical.setProperty(
          service + "Generation", TRUST.get(service).getProperty("generation"));
    }
    identityCanonical = directory.resolve("identity-r07-canonical.properties");
    store(identityCanonical, canonical);

    Properties admission = new Properties();
    admission.putAll(
        Map.of(
            "schemaVersion",
            "1",
            "enabled",
            "true",
            "realm",
            settings.realm(),
            "generation",
            settings.generation(),
            "topologyVersion",
            AdmissionWire.TOPOLOGY,
            "dbRunId",
            LAB.getProperty("lab.runId"),
            "identityPort",
            Integer.toString(IDENTITY_PRIVATE)));
    contentAdmission = directory.resolve("content-r07-canonical.properties");
    store(contentAdmission, admission);
  }

  private static void store(Path file, Properties properties) throws Exception {
    try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      properties.store(writer, "owned R07 canonical fixture; secrets never console output");
    }
  }

  private static String createAccount() throws Exception {
    String name = "r07canonical" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String invitation = maintenance("invite", name, null);
    String token = null;
    for (String line : invitation.lines().toList()) {
      if (line.startsWith("token=")) {
        token = line.substring(6);
      }
    }
    if (token == null
        || !maintenance("activate", "stdin", token + "\n" + PASSWORD + "\n")
            .trim()
            .equals("VERIFIED")) {
      throw new IllegalStateException("Canonical account activation unavailable");
    }
    return name;
  }

  private static String maintenance(String action, String argument, String input)
      throws Exception {
    var command =
        List.of(
            Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-Dloader.main=lab.backend.identity.credential.IdentityMaintenanceCli",
            "-cp",
            identityArtifact.toString(),
            "org.springframework.boot.loader.launch.PropertiesLauncher",
            action,
            argument);
    var builder = new ProcessBuilder(command).directory(directory.toFile());
    builder.redirectError(directory.resolve("r07-identity-maintenance.log").toFile());
    sanitize(builder);
    builder
        .environment()
        .putAll(
            Map.of(
                "IDENTITY_DB_URL",
                "jdbc:postgresql://127.0.0.1:"
                    + LAB.getProperty("lab.port")
                    + "/identity_db",
                "IDENTITY_DB_USER",
                "identity_app",
                "IDENTITY_DB_PASSWORD",
                LAB.getProperty("identity.app.password"),
                "IDENTITY_CORPUS_PATH",
                corpus.toString(),
                "IDENTITY_CORPUS_SHA256",
                corpusHash,
                "IDENTITY_THROTTLE_KEY",
                throttleKey));
    Process process = builder.start();
    try {
      try (var output = process.getOutputStream()) {
        if (input != null) {
          output.write(input.getBytes(StandardCharsets.UTF_8));
        }
      }
      if (!process.waitFor(25, TimeUnit.SECONDS) || process.exitValue() != 0) {
        throw new IllegalStateException("Canonical Identity maintenance unavailable");
      }
      return new String(process.getInputStream().readNBytes(4097), StandardCharsets.UTF_8);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  private static void startIdentity() throws Exception {
    closed(IDENTITY_PUBLIC);
    closed(IDENTITY_PRIVATE);
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-jar",
            identityArtifact.toString(),
            "--spring.profiles.active=canonical",
            "--server.port=" + IDENTITY_PUBLIC,
            "--backendlab.trust.config=" + directory.resolve("identity.properties"),
            "--backendlab.canonical.config=" + identityCanonical,
            "--logging.level.root=ERROR");
    builder
        .directory(directory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(
            ProcessBuilder.Redirect.appendTo(
                directory.resolve("r07-identity-canonical.log").toFile()));
    sanitize(builder);
    identityProcess = builder.start();
    awaitReady(identityProcess, "identity", IDENTITY_PUBLIC, "/api/v1/session");
  }

  private static void startContent() throws Exception {
    closed(CONTENT_PUBLIC);
    closed(CONTENT_PRIVATE);
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-jar",
            contentArtifact.toString(),
            "--spring.profiles.active=canonical",
            "--server.port=" + CONTENT_PUBLIC,
            "--backendlab.browser.enabled=true",
            "--backendlab.trust.config=" + contentTrust,
            "--backendlab.admission.config=" + contentAdmission,
            "--logging.level.root=ERROR");
    builder
        .directory(directory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(
            ProcessBuilder.Redirect.appendTo(
                directory.resolve("r07-content-canonical.log").toFile()));
    sanitize(builder);
    contentProcess = builder.start();
    awaitReady(contentProcess, "content", CONTENT_PUBLIC, "/api/v1/catalog");
  }

  private static void awaitReady(Process process, String receiver, int port, String path)
      throws Exception {
    // Packaged Identity/Content có thể warm up chậm trên Windows lab; vẫn giữ
    // deadline bounded để process/config lỗi không biến thành test treo vô hạn.
    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException("Canonical app exited; inspect retained R07 log");
      }
      try {
        if (call(
                    receiver,
                    port,
                    "GET",
                    path,
                    null,
                    Map.of("X-Backend-Lab-Client-IP", "127.0.0.1"))
                .statusCode()
            == 200) {
          return;
        }
      } catch (java.io.IOException startup) {
        // Listener chưa sẵn sàng.
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Canonical app startup timeout");
  }

  private static void stopIdentity() throws Exception {
    identityProcess = stop(identityProcess);
    closed(IDENTITY_PUBLIC);
    closed(IDENTITY_PRIVATE);
  }

  private static void stopContent() throws Exception {
    contentProcess = stop(contentProcess);
    closed(CONTENT_PUBLIC);
    closed(CONTENT_PRIVATE);
  }

  private static Process stop(Process process) throws Exception {
    if (process == null) {
      return null;
    }
    process.destroy();
    if (!process.waitFor(8, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
    }
    return null;
  }

  private static void closed(int port) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    int refused = 0;
    while (System.nanoTime() < deadline) {
      try (var socket = new Socket()) {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 150);
        refused = 0;
      } catch (ConnectException expected) {
        if (++refused == 2) {
          return;
        }
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("Named canonical listener remains reachable: " + port);
  }

  private static void sanitize(ProcessBuilder builder) {
    builder
        .environment()
        .keySet()
        .removeIf(
            key -> {
              String upper = key.toUpperCase(Locale.ROOT);
              return upper.startsWith("SPRING_")
                  || upper.startsWith("SERVER_")
                  || upper.startsWith("MANAGEMENT_")
                  || key.equals("JAVA_TOOL_OPTIONS")
                  || key.equals("_JAVA_OPTIONS");
            });
  }

  private static Login login(String account) throws Exception {
    var flowHeaders = identityHeaders(null);
    flowHeaders.put("X-Auth-Flow", "credential");
    HttpResponse<byte[]> flow =
        call(
            "identity",
            IDENTITY_PUBLIC,
            "POST",
            "/api/v1/session",
            bytes(Map.of("schemaVersion", 1)),
            flowHeaders);
    Login preauth = new Login(cookie(flow), json(flow, 200));
    HttpResponse<byte[]> response =
        call(
            "identity",
            IDENTITY_PUBLIC,
            "POST",
            "/api/v1/auth/login",
            bytes(
                Map.of(
                    "schemaVersion",
                    1,
                    "expectedContextId",
                    preauth.view().path("sessionContextId").asString(),
                    "loginName",
                    account,
                    "password",
                    PASSWORD)),
            identityHeaders(preauth));
    return new Login(cookie(response), json(response, 200));
  }

  private static HttpResponse<byte[]> logout(Login active) throws Exception {
    Map<String, String> headers = identityHeaders(active);
    headers.put("Idempotency-Key", UUID.randomUUID().toString());
    return call(
        "identity",
        IDENTITY_PUBLIC,
        "POST",
        "/api/v1/auth/logout",
        bytes(
            Map.of(
                "schemaVersion",
                1,
                "expectedContextId",
                active.view().path("sessionContextId").asString())),
        headers);
  }

  private static Map<String, String> identityHeaders(Login active) {
    var headers = new HashMap<String, String>();
    headers.put("X-Backend-Lab-Client-IP", "127.0.0.1");
    headers.put("Origin", ORIGIN);
    if (active != null) {
      headers.put("Cookie", COOKIE + "=" + active.cookie());
      headers.put("X-CSRF-TOKEN", active.view().path("csrfToken").asString());
    }
    return headers;
  }

  private static HttpResponse<byte[]> contentUnit(Login active) throws Exception {
    return call(
        "content",
        CONTENT_PUBLIC,
        "GET",
        "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01",
        null,
        Map.of(
            "Cookie",
            COOKIE + "=" + active.cookie(),
            "X-Session-Context",
            active.view().path("sessionContextId").asString()));
  }

  private static HttpResponse<byte[]> call(
      String receiver,
      int port,
      String method,
      String path,
      byte[] body,
      Map<String, String> headers)
      throws Exception {
    try (HttpClient client =
        HttpClient.newBuilder()
            .sslContext(
                TransportPeerClient.tls(
                    settings, settings.directory().resolve("edge.p12"), receiver))
            .connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
      var request =
          HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(8));
      if (body != null) {
        request.header("Content-Type", "application/json");
      }
      headers.forEach(request::header);
      return client.send(
          request
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofByteArray(body))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  private static JsonNode json(HttpResponse<byte[]> response, int status) {
    assertThat(response.statusCode()).isEqualTo(status);
    return WireCodec.strict(response.body());
  }

  private static String cookie(HttpResponse<byte[]> response) {
    List<String> values = response.headers().allValues("Set-Cookie");
    assertThat(values).hasSize(1);
    String pair = values.getFirst().split(";", 2)[0];
    assertThat(pair).startsWith(COOKIE + "=");
    return pair.substring(COOKIE.length() + 1);
  }

  private static byte[] bytes(Map<String, Object> value) throws Exception {
    return WireCodec.JSON.writeValueAsBytes(value);
  }

  private static void assertUnit(HttpResponse<byte[]> response) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    assertThat(new String(response.body(), StandardCharsets.UTF_8))
        .contains("UNIT-JAVA-01", "Java uses static types.")
        .doesNotContain(PASSWORD, login.cookie(), "principal", "admission");
  }

  private static void awaitContentFence(UUID context) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      try (Connection connection = source("content", "app").getConnection();
          var statement =
              connection.prepareStatement(
                  "SELECT count(*) FROM content.auth_session_fence WHERE context_id=?")) {
        statement.setObject(1, context);
        try (var result = statement.executeQuery()) {
          result.next();
          if (result.getLong(1) == 1) {
            return;
          }
        }
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Canonical Content fence was not delivered");
  }

  private static void seedBrowser() throws Exception {
    byte[] snapshot =
        Files.readAllBytes(
            Path.of(
                BrowserCanonicalIdentityIT.class
                    .getResource("/contracts/content/public/fixtures/browser-snapshot-v1.json")
                    .toURI()));
    try (Connection connection = contentMigrator.getConnection()) {
      BrowserPublicationFixture.ensureBaseline(
          connection,
          UUID.fromString(settings.realm()),
          UUID.fromString(settings.generation()),
          UUID.fromString(TRUST.get("learning").getProperty("generation")),
          "browser-canonical-capability-32-bytes-minimum"
              .getBytes(StandardCharsets.UTF_8));
      BrowserPublicationFixture.publish(connection, snapshot, "r07-browser-canonical");
    }
  }
}
