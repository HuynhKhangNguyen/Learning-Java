package lab.backend.learning.identityaccess;

import static lab.backend.learning.identityaccess.CanonicalThreeServiceRuntimeIT.*;
import static org.assertj.core.api.Assertions.*;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lab.backend.learning.journal.FilesystemJournalStore;
import lab.backend.learning.transport.WireCodec;
import lab.backend.learning.work.AllocationStore;
import lab.backend.learning.work.AllocationWire;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;

/** Three packaged services/own PG/mTLS và filesystem journal thật; không production/T1/T2. */
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(150)
class AllocationRuntimeIT {
  private static final byte[] CAPABILITY = "r08-owned-publication-capability-only".getBytes(StandardCharsets.UTF_8);
  private static Path journalRoot;

  @BeforeAll
  static void startAllocationAndNormalPublishedPool() throws Exception {
    CanonicalThreeServiceRuntimeIT.setup();
    stop("learning");
    journalRoot = directory.resolve("allocation-journal");
    var allocation = new Properties();
    allocation.putAll(Map.of("schemaVersion", "1", "enabled", "true",
        "realm", settings.realm(), "generation", settings.generation(),
        "dbRunId", settings.dbRunId(), "journalDirectory", journalRoot.toString()));
    store(directory.resolve("allocation.properties"), allocation);
    fixtureSql("content", "INSERT INTO content.publication_canonical_head(revision,bundle_id,bundle_digest) "
        + "VALUES(1,'r08-initial',repeat('0',64))");
    Files.write(directory.resolve("publication-coordinator.cap"), CAPABILITY);
    try (var connection = source("content", "migrator").getConnection();
        var statement = connection.prepareStatement(
            "INSERT INTO content.publication_environment(singleton,realm_id,producer_generation,"
                + "participant_generation,coordinator_capability_sha256,learning_ready_revision) "
                + "VALUES(true,?,?,?,sha256(?),1)")) {
      statement.setObject(1, UUID.fromString(settings.realm()));
      statement.setObject(2, UUID.fromString(trust.get("content").getProperty("generation")));
      statement.setObject(3, UUID.fromString(settings.generation()));
      statement.setBytes(4, CAPABILITY);
      statement.executeUpdate();
    }
    fixtureSql("learning", "INSERT INTO learning.publication_environment VALUES(true,'"
        + settings.realm() + "','" + settings.generation() + "','"
        + trust.get("content").getProperty("generation") + "',true)");
    fixtureSql("learning", "INSERT INTO learning.publication_head(singleton,applied_revision) VALUES(true,1)");
    fixtureSql("learning", "INSERT INTO learning.publication_delivery_cursor VALUES('content-global',0)");
    startAllocation();
    publish(root.resolve("contracts/fixtures/publication/fx05-candidate-cosmetic.json"),
        root.resolve("contracts/fixtures/publication/fx05-private-bundle.json"));
    // Historical v2 vẫn qua normal coordinator nhưng không được tự bổ sung allocation metadata.
    Login legacy = login();
    assertThat(create(legacy, UUID.randomUUID(), "assessment").statusCode()).isEqualTo(503);
    for (String name : new String[]{"a", "b", "practice"}) {
      publish(root.resolve("contracts/fixtures/exposure/r08-candidate-" + name + "-v3.json"),
          root.resolve("contracts/fixtures/exposure/r08-private-" + name + "-v3.json"));
    }
    assertThat(sqlLong("learning", "SELECT count(*) FROM learning.publication_operation WHERE state='RELEASED'"))
        .isEqualTo(4);
  }

  @AfterAll
  static void stopOwnedProcessesAndScanPrivateSentinels() throws Exception {
    try {
      CanonicalThreeServiceRuntimeIT.cleanup();
    } finally {
      if (directory != null) {
        Files.deleteIfExists(directory.resolve("publication-coordinator.cap"));
        for (String service : SERVICES) {
          Path log = directory.resolve(service + "-canonical.log");
          if (Files.exists(log)) assertThat(Files.readString(log))
              .doesNotContain("R08_PRIVATE_EXPECTED", new String(CAPABILITY, StandardCharsets.UTF_8));
        }
      }
    }
  }

  @Test
  void aaPackagedCreateReadRetryOwnerAndCanonicalAdmissionShapesAreBound() throws Exception {
    Login owner = login();
    UUID key = UUID.randomUUID();
    JsonNode ready = json(create(owner, key, "assessment"), 200);
    assertThat(ready.path("state").asString()).isEqualTo("ready");
    assertThat(ready.path("draft").path("revision").asString()).isEqualTo("0");
    assertThat(ready.path("independentAtAssignment").asBoolean()).isTrue();
    UUID attempt = UUID.fromString(ready.path("attemptId").asString());
    assertThat(json(create(owner, key, "assessment"), 200).path("attemptId")).isEqualTo(ready.path("attemptId"));
    assertThat(json(create(owner, UUID.randomUUID(), "assessment"), 200).path("attemptId"))
        .isEqualTo(ready.path("attemptId"));
    assertThat(json(read(owner, attempt), 200).path("prompt")).isEqualTo(ready.path("prompt"));
    assertThat(create(owner, key, "practice").statusCode()).isEqualTo(409);
    assertThat(read(login(), attempt).statusCode()).isEqualTo(404);
    assertThat(count("learning", "allocation_attempt", "user_id", owner.view().path("userId").asString())).isEqualTo(1);
    assertThat(count("learning", "allocation_journal", "user_id", owner.view().path("userId").asString())).isEqualTo(1);
    assertPrivateAbsent(ready);

    String path = "/internal/v1/auth-context/admissions";
    var readRequest = admissionRequest(owner, false);
    readRequest.put("audience", "learning");
    readRequest.put("action", "LEARNING_ATTEMPT_READ");
    readRequest.put("canonicalPath", "/api/v1/attempts/" + attempt);
    rebind(readRequest);
    JsonNode allowed = json(call("identity", "learning", 18441, "POST", path,
        bytes(readRequest), Map.of()), 200);
    assertThat(allowed.path("csrfVerified").asBoolean()).isFalse();
    for (String field : new String[]{"action", "method", "canonicalPath", "audience", "query", "bodySha256"}) {
      var wrong = new java.util.LinkedHashMap<>(readRequest);
      wrong.put(field, switch (field) {
        case "action" -> "LEARNING_ATTEMPT_CREATE";
        case "method" -> "POST";
        case "canonicalPath" -> "/api/v1/drafts/" + attempt;
        case "audience" -> "content";
        case "query" -> "owner=forged";
        default -> "0".repeat(64);
      });
      rebind(wrong);
      assertThat(call("identity", "learning", 18441, "POST", path, bytes(wrong), Map.of()).statusCode())
          .isEqualTo(403);
    }
    // Existing draft-save/Content body read hooks vẫn qua canonical authority thật.
    assertThat(probe("learning", owner, UUID.randomUUID()).path("state").asString()).isEqualTo("COMMITTED");
    assertThat(probe("content", owner, UUID.randomUUID()).path("state").asString()).isEqualTo("READ_COMMITTED");
  }

  @Test
  void abAnonymousPrivilegedPayloadContextCsrfOriginAndReadBodyCannotDisclose() throws Exception {
    Login owner = login();
    UUID attempt = UUID.fromString(json(create(owner, UUID.randomUUID(), "assessment"), 200)
        .path("attemptId").asString());
    var valid = allocationHeaders(owner);
    for (String field : new String[]{"Cookie", "X-Session-Context", "X-CSRF-TOKEN", "Origin"}) {
      var wrong = new java.util.HashMap<>(valid);
      wrong.put("Idempotency-Key", UUID.randomUUID().toString());
      wrong.remove(field);
      HttpResponse<byte[]> response = call("learning", "edge", 18083, "POST", "/api/v1/attempts",
          allocationBody("assessment"), wrong);
      assertThat(response.statusCode()).isIn(400, 401, 403);
      assertPrivateAbsent(WireCodec.strict(response.body()));
    }
    for (String field : new String[]{"owner", "case", "mode", "caseVersion", "graderVersion", "passed"}) {
      var body = new java.util.LinkedHashMap<String, Object>(Map.of(
          "schemaVersion", 1, "exerciseId", "EXERCISE-R08-GROUPING", "requestedMode", "assessment"));
      body.put(field, "forged");
      var headers = new java.util.HashMap<>(valid);
      headers.put("Idempotency-Key", UUID.randomUUID().toString());
      assertThat(call("learning", "edge", 18083, "POST", "/api/v1/attempts", bytes(body), headers).statusCode())
          .isEqualTo(400);
    }
    var wrongContext = new java.util.HashMap<>(valid);
    wrongContext.put("X-Session-Context", UUID.randomUUID().toString());
    assertThat(call("learning", "edge", 18083, "GET", "/api/v1/attempts/" + attempt, null, wrongContext)
        .statusCode()).isEqualTo(409);
    assertThat(call("learning", "edge", 18083, "GET", "/api/v1/attempts/" + attempt,
        "{}".getBytes(StandardCharsets.UTF_8), valid).statusCode()).isEqualTo(400);
    assertThat(call("learning", "edge", 18083, "GET", "/api/v1/attempts/" + attempt + "?x=1", null, valid)
        .statusCode()).isEqualTo(400);
  }

  @Test
  void acActualJournalFailureCrashAfterAckAndRestartRetryKeepSingleAssignment() throws Exception {
    Login owner = login();
    AdmissionRuntime admission = admissionRuntime();
    UUID attempt;
    UUID key = UUID.randomUUID();
    try (var access = admission.access()) {
      var store = new AllocationStore(UUID.fromString(settings.realm()), UUID.fromString(settings.generation()));
      attempt = access.execute(AdmissionWire.Request.createAttempt(owner.cookie(), context(owner),
          owner.view().path("csrfToken").asString(), ORIGIN, allocationBody("assessment")),
          (connection, user) -> store.create(connection, user, key,
              AllocationWire.create(allocationBody("assessment"))));
    }
    // Blocker rỗng chỉ chặn record của allocation mới; không sửa good journal records.
    Path blocker = journalRoot.resolve("learning-exposure-" + settings.realm())
        .resolve("records").resolve(journalOperation(attempt) + ".bin");
    Files.createDirectories(blocker);
    assertThat(json(create(owner, key, "assessment"), 202).path("state").asString()).isEqualTo("preparing");
    Files.delete(blocker);
    try (var held = source("learning", "app").getConnection(); var pool = Executors.newSingleThreadExecutor()) {
      held.setAutoCommit(false);
      try (var statement = held.createStatement()) {
        statement.executeQuery("SELECT operation_id FROM learning.allocation_journal WHERE attempt_id='"
            + attempt + "' FOR UPDATE").close();
      }
      var response = pool.submit(() -> read(owner, attempt));
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      boolean appended = false;
      while (System.nanoTime() < deadline) {
        try {
          var inventory = new FilesystemJournalStore(journalRoot.toAbsolutePath())
              .inventory("learning-exposure-" + settings.realm(), latestJournalHead());
          String operation = journalOperation(attempt);
          appended = inventory.entries().stream().anyMatch(entry -> entry.recordId().equals(operation));
          if (appended) break;
        } catch (Exception notYetCreated) { }
        Thread.sleep(20);
      }
      assertThat(appended).isTrue();
      assertThat(response.isDone()).isFalse();
      stop("learning");
      held.rollback();
      try { response.get(8, TimeUnit.SECONDS); } catch (java.util.concurrent.ExecutionException expectedCrash) { }
    }
    startAllocation();
    assertThat(json(read(owner, attempt), 200).path("state").asString()).isEqualTo("ready");
    assertThat(count("learning", "allocation_attempt", "user_id", owner.view().path("userId").asString())).isEqualTo(1);
    assertThat(count("learning", "allocation_journal", "user_id", owner.view().path("userId").asString())).isEqualTo(1);
  }

  /** Đọc fixed head của own filesystem fixture, rồi inventory tự verify toàn prefix. */
  private static String latestJournalHead() throws Exception {
    Path heads = journalRoot.resolve("learning-exposure-" + settings.realm()).resolve("heads");
    try (var paths = Files.list(heads)) {
      Path latest = paths.filter(path -> path.getFileName().toString().matches("[0-9]{19}\\.head"))
          .max(java.util.Comparator.comparing(path -> path.getFileName().toString())).orElseThrow();
      return Files.readAllLines(latest, StandardCharsets.UTF_8).get(1);
    }
  }

  private static AdmissionRuntime admissionRuntime() {
    var environment = new org.springframework.core.env.StandardEnvironment();
    environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("r08", Map.of(
        "backendlab.admission.config", directory.resolve("learning-canonical.properties").toString())));
    return AdmissionRuntime.load(environment, settings);
  }

  private static String journalOperation(UUID attempt) throws Exception {
    try (var connection = source("learning", "app").getConnection(); var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT operation_id::text FROM learning.allocation_journal "
            + "WHERE attempt_id='" + attempt + "'")) {
      rows.next();
      return rows.getString(1);
    }
  }

  private static void startAllocation() throws Exception {
    var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
        "-jar", jar("learning").toString(), "--spring.profiles.active=canonical", "--server.port=18083",
        "--backendlab.trust.config=" + directory.resolve("learning.properties"),
        "--backendlab.admission.config=" + directory.resolve("learning-canonical.properties"),
        "--backendlab.allocation.config=" + directory.resolve("allocation.properties"), "--logging.level.root=ERROR");
    builder.directory(directory.toFile()).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("learning-canonical.log").toFile()));
    sanitize(builder);
    Process process = builder.start();
    apps.put("learning", process);
    long deadline = System.nanoTime() + Duration.ofSeconds(35).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) throw new IllegalStateException("Allocation process exited; retained own log");
      try {
        if (call("learning", "edge", 18083, "GET", "/api/v1/transport/status", null, Map.of()).statusCode() == 200) return;
      } catch (java.io.IOException notReady) { }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Allocation startup timeout");
  }

  private static void publish(Path candidate, Path bundle) throws Exception {
    String operation = WireCodec.JSON.readTree(Files.readAllBytes(candidate)).path("operationId").asString();
    publicationCli("prepare", candidate.toString(), bundle.toString());
    publicationCli("start", operation);
    for (int phase = 0; phase < 3; phase++) publicationCli("dispatch", operation);
    assertThat(publicationCli("status", operation)).contains("state=COMPLETE");
  }

  private static String publicationCli(String... arguments) throws Exception {
    var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
        "-Dspring.profiles.active=canonical", "-Dloader.main=lab.backend.content.publication.PublicationLabCli",
        "-Dbackendlab.trust.config=" + directory.resolve("content.properties"), "-cp", jar("content").toString(),
        "org.springframework.boot.loader.launch.PropertiesLauncher"));
    command.addAll(List.of(arguments));
    var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
    sanitize(builder);
    Process process = builder.start();
    if (!process.waitFor(20, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("Owned publication CLI timeout");
    }
    String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(result).doesNotContain("R08_PRIVATE_EXPECTED");
    assertThat(process.exitValue()).as("publication CLI " + arguments[0] + " " + result).isZero();
    return result;
  }

  private static Map<String, String> allocationHeaders(Login login) {
    var values = new java.util.HashMap<String, String>();
    values.put("Cookie", COOKIE + "=" + login.cookie());
    values.put("X-Session-Context", login.view().path("sessionContextId").asString());
    values.put("X-CSRF-TOKEN", login.view().path("csrfToken").asString());
    values.put("Origin", ORIGIN);
    return values;
  }

  private static UUID context(Login login) {
    return UUID.fromString(login.view().path("sessionContextId").asString());
  }

  private static byte[] allocationBody(String mode) {
    return bytes(Map.of("schemaVersion", 1, "exerciseId", "EXERCISE-R08-GROUPING", "requestedMode", mode));
  }

  private static HttpResponse<byte[]> create(Login login, UUID key, String mode) throws Exception {
    var headers = new java.util.HashMap<>(allocationHeaders(login));
    headers.put("Idempotency-Key", key.toString());
    return call("learning", "edge", 18083, "POST", "/api/v1/attempts", allocationBody(mode), headers);
  }

  private static HttpResponse<byte[]> read(Login login, UUID attempt) throws Exception {
    return call("learning", "edge", 18083, "GET", "/api/v1/attempts/" + attempt, null, allocationHeaders(login));
  }

  private static void rebind(Map<String, Object> request) {
    request.put("requestBinding", AllocationWire.sha((request.get("method") + "\n"
        + request.get("canonicalPath") + "\n" + request.get("query") + "\n" + request.get("bodySha256"))
        .getBytes(StandardCharsets.UTF_8)));
  }

  private static long sqlLong(String service, String query) throws Exception {
    try (var connection = source(service, "app").getConnection(); var statement = connection.createStatement();
        var rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static void assertPrivateAbsent(JsonNode node) {
    String value = node.toString();
    assertThat(value).doesNotContain("R08_PRIVATE_EXPECTED", "rubricTargets", "graderVersion", "rubricVersion");
  }
}
