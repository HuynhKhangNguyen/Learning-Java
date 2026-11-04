package lab.backend.learning.identityaccess;

import static lab.backend.learning.identityaccess.CanonicalThreeServiceRuntimeIT.*;
import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  void abBigintEpochBoundaryAndResponseAllowlistRemainExact() throws Exception {
    Login owner = login();
    gateFixture("learning", "INSERT INTO learning.learner_guard(user_id,learning_epoch) VALUES ('"
        + owner.view().path("userId").asString() + "',9223372036854775807)");
    JsonNode ready = json(create(owner, UUID.randomUUID(), "assessment"), 200);
    assertThat(ready.path("learningEpoch").asString()).isEqualTo("9223372036854775807");
    assertThat(ready.path("draft").path("revision").asString()).isEqualTo("0");
    assertThat(ready.propertyNames()).containsExactlyInAnyOrderElementsOf(java.util.Set.of(
        "schemaVersion", "attemptId", "draftId", "learningEpoch", "state", "exerciseId", "mode",
        "caseVersion", "independentAtAssignment", "prompt", "input", "inputSchema", "draft"));
    UUID attempt = UUID.fromString(ready.path("attemptId").asString());
    assertThat(json(read(owner, attempt), 200).path("learningEpoch").asString())
        .isEqualTo("9223372036854775807");
  }

  @ParameterizedTest
  @ValueSource(strings = {"sameKey", "differentKeys"})
  void abConcurrentPackagedRequestsWaitOnActualAccountGateAndConverge(String kind) throws Exception {
    Login owner = login();
    UUID key = UUID.randomUUID();
    try (var access = admissionRuntime().access()) {
      // Chỉ prime canonical account guard, không reserve hoặc seed allocation.
      access.execute(AdmissionWire.Request.createAttempt(owner.cookie(), context(owner),
          owner.view().path("csrfToken").asString(), ORIGIN, allocationBody("assessment")),
          (connection, user) -> user);
    }
    try (var held = source("learning", "app").getConnection();
        var clients = client("learning", "edge");
        var executor = Executors.newFixedThreadPool(2)) {
      held.setAutoCommit(false);
      try (var statement = held.prepareStatement("SELECT user_id FROM learning.auth_account_gate "
          + "WHERE user_id=? FOR UPDATE")) {
        statement.setObject(1, UUID.fromString(owner.view().path("userId").asString()));
        statement.executeQuery().close();
      }
      var headersA = new java.util.HashMap<>(allocationHeaders(owner));
      headersA.put("Idempotency-Key", key.toString());
      var headersB = new java.util.HashMap<>(allocationHeaders(owner));
      headersB.put("Idempotency-Key", kind.equals("sameKey") ? key.toString() : UUID.randomUUID().toString());
      var first = executor.submit(() -> call(clients, 18083, "POST", "/api/v1/attempts",
          allocationBody("assessment"), headersA));
      var second = executor.submit(() -> call(clients, 18083, "POST", "/api/v1/attempts",
          allocationBody("assessment"), headersB));
      try {
        // PostgreSQL có thể xếp waiter thứ hai sau waiter đầu (soft blocker), nên
        // kiểm cả hai đang đợi Lock trên exact gate query thay vì buộc direct PID.
        awaitSqlLockWaitCount("%learning.auth_account_gate%", 2);
      } finally {
        held.rollback();
      }
      JsonNode a = json(first.get(8, TimeUnit.SECONDS), 200);
      JsonNode b = json(second.get(8, TimeUnit.SECONDS), 200);
      assertThat(a.path("attemptId")).isEqualTo(b.path("attemptId"));
      assertThat(a.path("independentAtAssignment").asBoolean()).isTrue();
    }
    String user = owner.view().path("userId").asString();
    for (String table : new String[]{"allocation_attempt", "allocation_journal", "exposure_floor"}) {
      assertThat(count("learning", table, "user_id", user)).isEqualTo(1);
    }
  }

  @Test
  void abValidWrongCsrfOriginCookieContextAndPrivilegedHeadersFailBeforeEffect() throws Exception {
    Login owner = login();
    for (String kind : new String[]{"csrf", "origin", "context", "crossCookie", "ownerHeader", "mode"}) {
      var values = new java.util.HashMap<>(allocationHeaders(owner));
      values.put("Idempotency-Key", UUID.randomUUID().toString());
      byte[] body = allocationBody("assessment");
      int expected;
      switch (kind) {
        case "csrf" -> {
          String csrf = owner.view().path("csrfToken").asString();
          values.put("X-CSRF-TOKEN", (csrf.startsWith("A") ? "B" : "A") + csrf.substring(1));
          expected = 403;
        }
        case "origin" -> {
          values.put("Origin", "https://wrong.invalid");
          expected = 403;
        }
        case "context" -> {
          values.put("X-Session-Context", UUID.randomUUID().toString());
          expected = 409;
        }
        case "crossCookie" -> {
          values.put("Cookie", COOKIE + "=" + login().cookie());
          expected = 409;
        }
        case "ownerHeader" -> {
          values.put("X-Owner-Id", owner.view().path("userId").asString());
          expected = 403;
        }
        case "mode" -> {
          body = allocationBody("clean");
          expected = 400;
        }
        default -> throw new IllegalStateException("Unknown negative fixture");
      }
      assertDeniedPrivate(call("learning", "edge", 18083, "POST", "/api/v1/attempts", body, values), expected);
    }
    assertThat(count("learning", "allocation_attempt", "user_id", owner.view().path("userId").asString())).isZero();
  }

  @Test
  void acActualJournalFailureCrashAfterAckAndRestartRetryKeepSingleAssignment() throws Exception {
    Prepared pending = preparePending(login());
    try (var barrier = JournalAckBarrier.start(pending.owner, pending.attempt)) {
      // UPDATE receipt đang đợi đúng held PID: append + inventory đã trả verified ACK,
      // nhưng DB chưa lưu receipt và final admission/snapshot chưa thể chạy.
      assertJournalCovered(pending.attempt);
      assertThat(journalState(pending.attempt)).isEqualTo("PENDING");
      stop("learning");
      barrier.release();
      try {
        barrier.response.get(8, TimeUnit.SECONDS);
      } catch (java.util.concurrent.ExecutionException expectedCrash) {
        assertThat(expectedCrash.getCause()).isInstanceOf(java.io.IOException.class);
      }
    }
    startAllocation();
    assertThat(json(read(pending.owner, pending.attempt), 200).path("state").asString()).isEqualTo("ready");
    String owner = pending.owner.view().path("userId").asString();
    assertThat(count("learning", "allocation_attempt", "user_id", owner)).isEqualTo(1);
    assertThat(count("learning", "allocation_journal", "user_id", owner)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"logout", "contextExpiry", "credentialFloor"})
  void adCanonicalGateChangesAfterJournalAckBeforeFinalDenyDisclosure(String kind) throws Exception {
    Prepared pending = preparePending(login());
    String operation;
    try (var barrier = JournalAckBarrier.start(pending.owner, pending.attempt)) {
      assertJournalCovered(pending.attempt);
      operation = changeGate(pending.owner, kind);
      barrier.release();
      assertDeniedPrivate(barrier.response.get(8, TimeUnit.SECONDS), 401);
    }
    assertThat(attemptState(pending.attempt)).isEqualTo("PREPARING");
    if (operation != null) awaitCompleted(operation);
  }

  @ParameterizedTest
  @ValueSource(strings = {"epoch", "pending", "deleted"})
  void aeLocalLifecycleChangesAfterJournalAckBeforeFinalDenyDisclosure(String kind) throws Exception {
    Prepared pending = preparePending(login());
    try (var barrier = JournalAckBarrier.start(pending.owner, pending.attempt)) {
      assertJournalCovered(pending.attempt);
      changeGate(pending.owner, kind);
      barrier.release();
      assertDeniedPrivate(barrier.response.get(8, TimeUnit.SECONDS), 409);
    }
    assertThat(attemptState(pending.attempt)).isEqualTo("PREPARING");
  }

  @ParameterizedTest
  @ValueSource(strings = {"logout", "contextExpiry", "credentialFloor", "epoch", "pending", "deleted"})
  void afReadyReadRechecksGatesAfterPhysicalJournalBarrier(String kind) throws Exception {
    Login owner = login();
    UUID attempt = UUID.fromString(json(create(owner, UUID.randomUUID(), "assessment"), 200)
        .path("attemptId").asString());
    String operation;
    try (var barrier = JournalReadBarrier.start(owner, attempt)) {
      operation = changeGate(owner, kind);
      barrier.release();
      assertDeniedPrivate(barrier.response.get(8, TimeUnit.SECONDS),
          java.util.Set.of("logout", "contextExpiry", "credentialFloor").contains(kind) ? 401 : 409);
    }
    // READY đã commit ở allocation cũ; deny read không được xóa good work/exposure.
    assertThat(attemptState(attempt)).isEqualTo("READY");
    if (operation != null) awaitCompleted(operation);
  }

  @Test
  void agCommittedReadySnapshotPrecedesLaterGateAndSubsequentReadIsDenied() throws Exception {
    Login owner = login();
    JsonNode snapshot = json(create(owner, UUID.randomUUID(), "assessment"), 200);
    UUID attempt = UUID.fromString(snapshot.path("attemptId").asString());
    assertThat(snapshot.path("state").asString()).isEqualTo("ready");
    assertThat(snapshot.has("prompt")).isTrue();
    changeGate(owner, "pending");
    assertDeniedPrivate(read(owner, attempt), 409);
    assertThat(snapshot.path("state").asString()).isEqualTo("ready");
    assertThat(attemptState(attempt)).isEqualTo("READY");
  }

  @Test
  void ahPublicationFenceReleaseWithdrawWinsBetweenJournalAckAndFinal() throws Exception {
    Prepared pending = preparePending(login());
    Path candidate = root.resolve("contracts/fixtures/exposure/r08-candidate-withdraw-v3.json");
    Path bundle = root.resolve("contracts/fixtures/exposure/r08-private-withdraw-v3.json");
    String operation = WireCodec.JSON.readTree(Files.readAllBytes(candidate)).path("operationId").asString();
    publicationCli("prepare", candidate.toString(), bundle.toString());
    publicationCli("start", operation);
    publicationCli("dispatch", operation); // STAGE không làm mất quyền đọc current head.
    try (var dispatcher = PublicationDispatchBarrier.start(operation);
        var receipt = JournalAckBarrier.start(pending.owner, pending.attempt)) {
      assertJournalCovered(pending.attempt);
      dispatcher.dispatch(); // Actual packaged product CLI FENCE + canonical commit.
      assertThat(sqlLong("learning", "SELECT count(*) FROM learning.publication_head "
          + "WHERE fence_operation IS NOT NULL")).isEqualTo(1);
      receipt.release();
      assertDeniedPrivate(receipt.response.get(8, TimeUnit.SECONDS), 503);
    }
    publicationCli("dispatch", operation); // RELEASE đưa explicit old-version WITHDRAW vào effect.
    assertThat(publicationCli("status", operation)).contains("state=COMPLETE");
    assertDeniedPrivate(read(pending.owner, pending.attempt), 409);
    assertThat(attemptState(pending.attempt)).isEqualTo("PREPARING");
    assertThat(journalState(pending.attempt)).isEqualTo("VERIFIED");
  }

  @Test
  void aiMissingCurrentTargetAndActiveLegacyDenyAbsenceFallback() throws Exception {
    Login owner = login();
    UUID oldAttempt = UUID.fromString(json(create(owner, UUID.randomUUID(), "assessment"), 200)
        .path("attemptId").asString());
    publish(root.resolve("contracts/fixtures/exposure/r08-candidate-target-mismatch-v3.json"),
        root.resolve("contracts/fixtures/exposure/r08-private-target-mismatch-v3.json"));
    assertDeniedPrivate(read(owner, oldAttempt), 409);
    assertDeniedPrivate(create(login(), UUID.randomUUID(), "assessment"), 503);
    publish(root.resolve("contracts/fixtures/exposure/r08-candidate-late-v2.json"),
        root.resolve("contracts/fixtures/exposure/r08-private-late-v2.json"));
    assertDeniedPrivate(create(login(), UUID.randomUUID(), "assessment"), 503);
    assertDeniedPrivate(read(owner, oldAttempt), 409);
  }

  @Test
  void ajMalformedMetadataAndImmutableFamilyConflictsKeepBothGoodHeads() throws Exception {
    long contentHead = sqlLong("content", "SELECT revision FROM content.publication_canonical_head WHERE singleton");
    long learningHead = sqlLong("learning", "SELECT applied_revision FROM learning.publication_head WHERE singleton");
    long bundles = sqlLong("content", "SELECT count(*) FROM content.publication_bundle");
    long candidates = sqlLong("content", "SELECT count(*) FROM content.publication_candidate");
    long replicas = sqlLong("learning", "SELECT count(*) FROM learning.content_replica");
    for (String name : new String[]{"family-conflict", "immutable-conflict", "bad-schema", "missing-allocation", "bad-ref"}) {
      publicationCliRejected("prepare",
          root.resolve("contracts/fixtures/exposure/r08-candidate-" + name + "-v3.json").toString(),
          root.resolve("contracts/fixtures/exposure/r08-private-" + name + "-v3.json").toString());
      assertThat(sqlLong("content", "SELECT revision FROM content.publication_canonical_head WHERE singleton"))
          .isEqualTo(contentHead);
      assertThat(sqlLong("learning", "SELECT applied_revision FROM learning.publication_head WHERE singleton"))
          .isEqualTo(learningHead);
      assertThat(sqlLong("content", "SELECT count(*) FROM content.publication_bundle")).isEqualTo(bundles);
      assertThat(sqlLong("content", "SELECT count(*) FROM content.publication_candidate")).isEqualTo(candidates);
      assertThat(sqlLong("learning", "SELECT count(*) FROM learning.content_replica")).isEqualTo(replicas);
    }
  }

  /** Process control chỉ chờ GO; production CLI vẫn validate capability, lease, ACK và DB effects. */
  private static final class PublicationDispatchBarrier implements AutoCloseable {
    private final Process process;
    private final java.io.BufferedReader output;

    private PublicationDispatchBarrier(String operation) throws Exception {
      var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
          "-Dspring.profiles.active=canonical",
          "-Dloader.main=lab.backend.content.publication.PublicationDispatchFixtureMain",
          "-Dloader.path=" + root.resolve("backend/services/content-service/target/test-classes"),
          "-Dbackendlab.trust.config=" + directory.resolve("content.properties"), "-cp", jar("content").toString(),
          "org.springframework.boot.loader.launch.PropertiesLauncher", "dispatch", operation));
      var builder = new ProcessBuilder(command).directory(directory.toFile());
      sanitize(builder);
      builder.redirectError(ProcessBuilder.Redirect.appendTo(directory.resolve("publication-dispatch-barrier.log").toFile()));
      process = builder.start();
      output = process.inputReader(StandardCharsets.UTF_8);
      try (var reader = Executors.newSingleThreadExecutor()) {
        assertThat(reader.submit(() -> output.readLine()).get(15, TimeUnit.SECONDS)).isEqualTo("READY");
      } catch (Exception | AssertionError failure) {
        close();
        throw failure;
      }
    }

    static PublicationDispatchBarrier start(String operation) throws Exception {
      return new PublicationDispatchBarrier(operation);
    }

    void dispatch() throws Exception {
      process.getOutputStream().write("GO\n".getBytes(StandardCharsets.UTF_8));
      process.getOutputStream().flush();
      process.getOutputStream().close();
      assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      assertThat(process.exitValue()).isZero();
      assertThat(output.readLine()).contains("PUBLICATION_DISPATCH").contains("sent=true");
    }

    public void close() throws Exception {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      }
      output.close();
    }
  }

  /** Pending được reserve bằng actual domain + canonical admission, rồi packaged retry
   * chứng minh lỗi trước append trả202 không prompt; không ghi fixture attempt bằng SQL. */
  private static Prepared preparePending(Login owner) throws Exception {
    UUID key = UUID.randomUUID();
    UUID attempt;
    try (var access = admissionRuntime().access()) {
      var allocation = new AllocationStore(UUID.fromString(settings.realm()),
          UUID.fromString(settings.generation()));
      attempt = access.execute(AdmissionWire.Request.createAttempt(owner.cookie(), context(owner),
          owner.view().path("csrfToken").asString(), ORIGIN, allocationBody("assessment")),
          (connection, user) -> allocation.create(connection, user, key,
              AllocationWire.create(allocationBody("assessment"))));
    }
    Path blocker = journalRoot.resolve("learning-exposure-" + settings.realm())
        .resolve("records").resolve(journalOperation(attempt) + ".bin");
    Files.createDirectories(blocker);
    try {
      JsonNode preparing = json(create(owner, key, "assessment"), 202);
      assertThat(preparing.path("state").asString()).isEqualTo("preparing");
      assertThat(preparing.has("prompt")).isFalse();
      assertThat(preparing.has("input")).isFalse();
      assertThat(preparing.has("inputSchema")).isFalse();
    } finally {
      Files.delete(blocker);
    }
    return new Prepared(owner, key, attempt);
  }

  private record Prepared(Login owner, UUID key, UUID attempt) {}

  /** Đợi PID của packaged app mắc ở UPDATE receipt, không chỉ quan sát file đã tồn tại. */
  private static final class JournalAckBarrier implements AutoCloseable {
    private final Connection held;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<HttpResponse<byte[]>> response;
    private boolean released;

    private JournalAckBarrier(Login owner, UUID attempt) throws Exception {
      held = source("learning", "app").getConnection();
      held.setAutoCommit(false);
      int blocker;
      try (var statement = held.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
        rows.next();
        blocker = rows.getInt(1);
      }
      try (var statement = held.prepareStatement(
          "SELECT operation_id FROM learning.allocation_journal WHERE attempt_id=? FOR UPDATE")) {
        statement.setObject(1, attempt);
        statement.executeQuery().close();
      }
      response = executor.submit(() -> read(owner, attempt));
      try {
        awaitSqlWait(blocker, "UPDATE learning.allocation_journal SET state");
        assertThat(response.isDone()).isFalse();
      } catch (Exception | AssertionError failure) {
        close();
        throw failure;
      }
    }

    static JournalAckBarrier start(Login owner, UUID attempt) throws Exception {
      return new JournalAckBarrier(owner, attempt);
    }

    void release() throws Exception {
      if (!released) {
        held.rollback();
        released = true;
      }
    }

    public void close() throws Exception {
      // Thả DB lock trước khi chờ executor để assertion lỗi không gây deadlock cleanup.
      try {
        release();
      } finally {
        held.close();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(8, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  /** Read đã commit locate admission rồi bị giữ ở physical head read ngoài DB locks. */
  private static final class JournalReadBarrier implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<HttpResponse<byte[]>> response;
    private boolean released;

    private JournalReadBarrier(Login owner, UUID attempt) throws Exception {
      long uses = count("learning", "auth_admission_use", "user_id", owner.view().path("userId").asString());
      channel = FileChannel.open(latestHeadPath(), java.nio.file.StandardOpenOption.READ,
          java.nio.file.StandardOpenOption.WRITE);
      lock = channel.lock();
      response = executor.submit(() -> read(owner, attempt));
      try {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (count("learning", "auth_admission_use", "user_id", owner.view().path("userId").asString()) <= uses) {
          if (System.nanoTime() >= deadline) throw new IllegalStateException("Read locate admission did not commit");
          Thread.sleep(20);
        }
        assertThat(response.isDone()).isFalse();
      } catch (Exception | AssertionError failure) {
        close();
        throw failure;
      }
    }

    static JournalReadBarrier start(Login owner, UUID attempt) throws Exception {
      return new JournalReadBarrier(owner, attempt);
    }

    void release() throws Exception {
      if (!released) {
        lock.release();
        released = true;
      }
    }

    public void close() throws Exception {
      try {
        release();
      } finally {
        channel.close();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(8, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  private static void awaitSqlWait(int blocker, String queryPrefix) throws Exception {
    awaitSqlWaitCount(blocker, queryPrefix + "%", 1);
  }

  private static void awaitSqlWaitCount(int blocker, String queryLike, int expected) throws Exception {
    // Chờ đúng PostgreSQL lock waiter; Windows CI có thể mất vài giây cho mTLS admission
    // trước khi request tới ACK UPDATE, nên timeout ngắn không được biến thành race theo máy.
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      try (var connection = source("learning", "app").getConnection();
          var statement = connection.prepareStatement(
              "SELECT count(*) FROM pg_stat_activity WHERE datname='learning_db' "
                  + "AND ?=ANY(pg_blocking_pids(pid)) AND query LIKE ?")) {
        statement.setInt(1, blocker);
        statement.setString(2, queryLike);
        try (var rows = statement.executeQuery()) {
          rows.next();
          if (rows.getInt(1) == expected) return;
        }
      }
      Thread.sleep(20);
    }
    throw new IllegalStateException("Packaged journal ACK UPDATE did not reach exact DB barrier");
  }

  private static void awaitSqlLockWaitCount(String queryLike, int expected) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (System.nanoTime() < deadline) {
      try (var connection = source("learning", "app").getConnection();
          var statement = connection.prepareStatement(
              "SELECT count(*) FROM pg_stat_activity WHERE datname='learning_db' "
                  + "AND wait_event_type='Lock' AND query LIKE ?")) {
        statement.setString(1, queryLike);
        try (var rows = statement.executeQuery()) {
          rows.next();
          if (rows.getInt(1) == expected) return;
        }
      }
      Thread.sleep(20);
    }
    throw new IllegalStateException("Packaged requests did not reach exact account-gate barrier");
  }

  private static String changeGate(Login owner, String kind) throws Exception {
    String user = owner.view().path("userId").asString();
    String context = owner.view().path("sessionContextId").asString();
    switch (kind) {
      case "logout" -> {
        var values = headers(owner);
        values.put("Idempotency-Key", UUID.randomUUID().toString());
        return json(call("identity", "edge", 18081, "POST", "/api/v1/auth/logout",
            bytes(Map.of("schemaVersion", 1, "expectedContextId", context)), values), 202)
            .path("operationId").asString();
      }
      case "contextExpiry" -> gateFixture("identity",
          "DELETE FROM identity.spring_session WHERE session_id="
              + "(SELECT framework_id FROM identity.auth_session "
              + "WHERE context_id='" + context + "')");
      case "credentialFloor" -> gateFixture("identity",
          "UPDATE identity.app_user SET credential_epoch=credential_epoch+1 WHERE user_id='" + user + "'");
      case "epoch" -> gateFixture("learning",
          "UPDATE learning.learner_guard SET learning_epoch=learning_epoch+1 WHERE user_id='" + user + "'");
      case "pending", "deleted" -> gateFixture("learning",
          "UPDATE learning.learner_guard SET lifecycle_state='" + kind.toUpperCase(java.util.Locale.ROOT)
              + "' WHERE user_id='" + user + "'");
      default -> throw new IllegalArgumentException("Unknown controlled gate fixture");
    }
    return null;
  }

  /** Operator fixture chỉ đổi một guard; không giả full reset/delete/credential workflow. */
  private static void gateFixture(String service, String sql) throws Exception {
    try (var connection = source(service, "migrator").getConnection(); var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute("SET LOCAL lock_timeout='1s'; SET LOCAL statement_timeout='2s'");
      assertThat(statement.executeUpdate(sql)).isEqualTo(1);
      connection.commit();
    }
  }

  private static void assertDeniedPrivate(HttpResponse<byte[]> response, int expected) {
    JsonNode node = json(response, expected);
    assertPrivateAbsent(node);
    assertThat(node.has("prompt")).isFalse();
    assertThat(node.has("input")).isFalse();
    assertThat(node.has("inputSchema")).isFalse();
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
  }

  private static String attemptState(UUID attempt) throws Exception {
    return allocationText("SELECT state FROM learning.allocation_attempt WHERE attempt_id='" + attempt + "'");
  }

  private static String journalState(UUID attempt) throws Exception {
    return allocationText("SELECT state FROM learning.allocation_journal WHERE attempt_id='" + attempt + "'");
  }

  private static String allocationText(String query) throws Exception {
    try (var connection = source("learning", "app").getConnection(); var statement = connection.createStatement();
        var rows = statement.executeQuery(query)) {
      if (!rows.next()) throw new IllegalStateException("Allocation fixture row missing");
      return rows.getString(1);
    }
  }

  private static void assertJournalCovered(UUID attempt) throws Exception {
    String operation = journalOperation(attempt);
    var inventory = new FilesystemJournalStore(journalRoot.toAbsolutePath())
        .inventory("learning-exposure-" + settings.realm(), latestJournalHead());
    assertThat(inventory.entries().stream().anyMatch(entry -> entry.recordId().equals(operation))).isTrue();
  }

  /** Đọc fixed head của own filesystem fixture, rồi inventory tự verify toàn prefix. */
  private static String latestJournalHead() throws Exception {
    return Files.readAllLines(latestHeadPath(), StandardCharsets.UTF_8).get(1);
  }

  private static Path latestHeadPath() throws Exception {
    Path heads = journalRoot.resolve("learning-exposure-" + settings.realm()).resolve("heads");
    try (var paths = Files.list(heads)) {
      return paths.filter(path -> path.getFileName().toString().matches("[0-9]{19}\\.head"))
          .max(java.util.Comparator.comparing(path -> path.getFileName().toString())).orElseThrow();
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
    CliResult result = runPublicationCli(arguments);
    if (result.exit != 0 && "prepare".equals(arguments[0])) {
      // TransportSettings giữ clock/connect budget chặt và CLI cố ý chỉ trả
      // một lỗi an toàn. Retry đúng một exact candidate vẫn đi lại toàn bộ
      // validation/DB guard; prepare idempotent nên cũng cover lost response.
      result = runPublicationCli(arguments);
    }
    assertThat(result.output).doesNotContain("R08_PRIVATE_EXPECTED");
    assertThat(result.exit).as("publication CLI " + arguments[0] + " " + result.output).isZero();
    return result.output;
  }

  private static void publicationCliRejected(String... arguments) throws Exception {
    CliResult result = runPublicationCli(arguments);
    assertThat(result.exit).isEqualTo(1);
    assertThat(result.output).doesNotContain("R08_PRIVATE_EXPECTED");
    assertThat(result.output.lines().filter(line -> !line.startsWith("NOTE: Picked up JDK_JAVA_OPTIONS:"))
        .filter(line -> !line.isBlank()).toList()).containsExactly("PUBLICATION_CANDIDATE_REJECTED");
  }

  private static CliResult runPublicationCli(String... arguments) throws Exception {
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
      assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
      throw new IllegalStateException("Owned publication CLI timeout");
    }
    return new CliResult(process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  private record CliResult(int exit, String output) {}

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
