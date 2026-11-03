package lab.backend.learning.work;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lab.backend.learning.identityaccess.AdmissionGate;
import lab.backend.learning.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/**
 * Local SQL của allocation. Caller phải giữ auth_account_gate qua AdmissionGate;
 * store khóa learner_guard rồi shared publication gate rồi resource trong cùng Connection.
 * Không gọi network/journal và không mở transaction lồng trong callback này.
 */
public final class AllocationStore {
  private final String namespace;
  private final UUID realm;
  private final UUID generation;

  public AllocationStore(UUID realm, UUID generation) {
    this.realm = realm;
    this.generation = generation;
    namespace = "learning-exposure-" + realm;
  }

  public UUID create(Connection connection, UUID owner, UUID key, AllocationWire.Create request)
      throws SQLException {
    long epoch = guard(connection, owner);
    AllocationPublication publication = AllocationPublication.lock(connection, realm, generation);
    UUID retry = receipt(connection, owner, key, request.fingerprint());
    if (retry != null) {
      requireCurrent(connection, owner, epoch, retry);
      return retry;
    }
    var pool = publication.pool(connection, request);
    if (pool.isEmpty()) throw AllocationWire.deny(503, "ALLOCATION_CONTENT_UNAVAILABLE");
    Set<String> groups = new java.util.HashSet<>();
    for (var candidate : pool) groups.add(candidate.allocation().path("exposureGroup").asString());
    if (groups.size() != 1) throw AllocationWire.deny(503, "ALLOCATION_POOL_CONFLICT");
    String group = groups.iterator().next();
    UUID active = active(connection, owner, epoch, group, request);
    if (active != null) {
      // Different key resume cùng assignment, không consume family/operation khác.
      saveReceipt(connection, owner, epoch, key, request.fingerprint(), active);
      return active;
    }
    AllocationPublication.Candidate selected = null;
    for (var candidate : pool) {
      if (request.requestedMode().equals("practice")
          || !known(connection, owner, candidate.allocation().path("equivalenceKey").asString())) {
        selected = candidate;
        break;
      }
    }
    if (selected == null) throw AllocationWire.deny(409, "CASE_EXHAUSTED");
    UUID attempt = UUID.randomUUID();
    UUID draft = UUID.randomUUID();
    UUID operation = UUID.randomUUID();
    insertAttempt(connection, owner, epoch, attempt, draft, operation, selected);
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.allocation_draft(draft_id,attempt_id,user_id,learning_epoch) "
            + "VALUES (?,?,?,?)")) {
      statement.setObject(1, draft);
      statement.setObject(2, attempt);
      statement.setObject(3, owner);
      statement.setLong(4, epoch);
      statement.executeUpdate();
    }
    var metadata = selected.allocation();
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.exposure_floor(user_id,equivalence_key,family_id,exposure_group,"
            + "first_operation_id) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING")) {
      statement.setObject(1, owner);
      statement.setString(2, metadata.path("equivalenceKey").asString());
      statement.setString(3, metadata.path("familyId").asString());
      statement.setString(4, group);
      statement.setObject(5, operation);
      statement.executeUpdate();
    }
    insertJournal(connection, owner, epoch, attempt, operation, selected);
    saveReceipt(connection, owner, epoch, key, request.fingerprint(), attempt);
    return attempt;
  }

  /** Authorize owner trước journal I/O; không đọc prompt vào response hoặc chốt READY. */
  public UUID locate(Connection connection, UUID owner, UUID attempt) throws SQLException {
    long epoch = guard(connection, owner);
    AllocationPublication publication = AllocationPublication.lock(connection, realm, generation);
    Attempt row = requireCurrent(connection, owner, epoch, attempt);
    publication.pinned(connection, row.bundleId);
    return attempt;
  }

  /** Read/final snapshot luôn recheck owner/epoch/policy; READY không cache quyền disclosure. */
  public Map<String, Object> disclose(Connection connection, UUID owner, UUID attempt,
      boolean verifiedThisRequest) throws SQLException {
    long epoch = guard(connection, owner);
    AllocationPublication publication = AllocationPublication.lock(connection, realm, generation);
    Attempt row = requireCurrent(connection, owner, epoch, attempt);
    var candidate = publication.pinned(connection, row.bundleId);
    if (!candidate.bundleDigest().equals(row.bundleDigest)
        || !candidate.manifestDigest().equals(row.manifestDigest)) {
      throw AllocationWire.deny(503, "ALLOCATION_PIN_CONFLICT");
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", 1);
    result.put("attemptId", attempt.toString());
    result.put("draftId", row.draft.toString());
    result.put("learningEpoch", Long.toString(epoch));
    if (!verifiedThisRequest || !journalVerified(connection, row.operation, owner, attempt, epoch)) {
      result.put("state", "preparing");
      return result;
    }
    if (row.state.equals("CLOSED")) throw AllocationWire.deny(409, "ATTEMPT_CLOSED");
    try (var statement = connection.prepareStatement(
        "UPDATE learning.allocation_attempt SET state='READY' "
            + "WHERE attempt_id=? AND state='PREPARING'")) {
      statement.setObject(1, attempt);
      statement.executeUpdate();
    }
    // Allowlist tường minh, không spread allocation/expected/rubricTargets ra response.
    JsonNode metadata = candidate.allocation();
    result.put("state", "ready");
    result.put("exerciseId", metadata.path("exerciseId").asString());
    result.put("mode", metadata.path("mode").asString());
    result.put("caseVersion", metadata.path("caseVersion").asString());
    result.put("independentAtAssignment", row.independent);
    result.put("prompt", metadata.path("prompt"));
    result.put("input", metadata.path("input"));
    result.put("inputSchema", metadata.path("inputSchema"));
    try (var statement = connection.prepareStatement(
        "SELECT revision,answer,position FROM learning.allocation_draft "
            + "WHERE draft_id=? AND attempt_id=? AND user_id=? AND learning_epoch=?")) {
      statement.setObject(1, row.draft);
      statement.setObject(2, attempt);
      statement.setObject(3, owner);
      statement.setLong(4, epoch);
      try (var draft = statement.executeQuery()) {
        if (!draft.next()) throw AllocationWire.deny(503, "DRAFT_UNAVAILABLE");
        result.put("draft", Map.of("revision", Long.toString(draft.getLong(1)),
            "answer", WireCodec.JSON.readTree(draft.getString(2)),
            "position", WireCodec.JSON.readTree(draft.getString(3))));
      }
    }
    return result;
  }

  /** R11 consume port: thiếu receipt/preparing không thể dùng allocation làm T1 evidence. */
  public void requireReadyForSubmission(Connection connection, UUID owner, UUID attempt)
      throws SQLException {
    long epoch = guard(connection, owner);
    AllocationPublication publication = AllocationPublication.lock(connection, realm, generation);
    Attempt row = requireCurrent(connection, owner, epoch, attempt);
    publication.pinned(connection, row.bundleId);
    if (!row.state.equals("READY")
        || !journalVerified(connection, row.operation, owner, attempt, epoch)) {
      throw AllocationWire.deny(409, "ALLOCATION_PREPARING");
    }
  }

  private static long guard(Connection connection, UUID owner) throws SQLException {
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.learner_guard(user_id) VALUES (?) ON CONFLICT DO NOTHING")) {
      statement.setObject(1, owner);
      statement.executeUpdate();
    }
    try (var statement = connection.prepareStatement(
        "SELECT learning_epoch,lifecycle_state FROM learning.learner_guard "
            + "WHERE user_id=? FOR UPDATE")) {
      statement.setObject(1, owner);
      try (var rows = statement.executeQuery()) {
        if (!rows.next() || !rows.getString(2).equals("ACTIVE")) {
          throw AllocationWire.deny(409, "LEARNER_LIFECYCLE_PENDING");
        }
        return rows.getLong(1);
      }
    }
  }

  private static UUID receipt(Connection connection, UUID owner, UUID key, String fingerprint)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT fingerprint,attempt_id FROM learning.allocation_receipt "
            + "WHERE user_id=? AND operation='ALLOCATION' AND idempotency_key=?")) {
      statement.setObject(1, owner);
      statement.setObject(2, key);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) return null;
        if (!rows.getString(1).equals(fingerprint)) throw AllocationWire.deny(409, "KEY_REUSED");
        return rows.getObject(2, UUID.class);
      }
    }
  }

  private static UUID active(Connection connection, UUID owner, long epoch, String group,
      AllocationWire.Create request) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT attempt_id,exercise_id,mode FROM learning.allocation_attempt WHERE user_id=? "
            + "AND learning_epoch=? AND exposure_group=? AND state IN ('PREPARING','READY')")) {
      statement.setObject(1, owner);
      statement.setLong(2, epoch);
      statement.setString(3, group);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) return null;
        if (!rows.getString(2).equals(request.exerciseId())
            || !rows.getString(3).equals(request.requestedMode())) {
          throw AllocationWire.deny(409, "ASSIGNMENT_ALREADY_OPEN");
        }
        return rows.getObject(1, UUID.class);
      }
    }
  }

  private static boolean known(Connection connection, UUID owner, String equivalence)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT 1 FROM learning.exposure_floor WHERE user_id=? AND equivalence_key=?")) {
      statement.setObject(1, owner);
      statement.setString(2, equivalence);
      try (var rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  private static void insertAttempt(Connection connection, UUID owner, long epoch,
      UUID attempt, UUID draft, UUID operation, AllocationPublication.Candidate selected)
      throws SQLException {
    var metadata = selected.allocation();
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.allocation_attempt(attempt_id,user_id,learning_epoch,draft_id,"
            + "operation_id,exercise_id,mode,case_id,case_version,family_id,exposure_group,"
            + "equivalence_key,rubric_version,grader_version,bundle_id,bundle_sha256,"
            + "manifest_sha256,independent_at_assignment) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
      statement.setObject(1, attempt);
      statement.setObject(2, owner);
      statement.setLong(3, epoch);
      statement.setObject(4, draft);
      statement.setObject(5, operation);
      statement.setString(6, metadata.path("exerciseId").asString());
      statement.setString(7, metadata.path("mode").asString());
      statement.setString(8, selected.bundle().path("id").asString());
      statement.setString(9, metadata.path("caseVersion").asString());
      statement.setString(10, metadata.path("familyId").asString());
      statement.setString(11, metadata.path("exposureGroup").asString());
      statement.setString(12, metadata.path("equivalenceKey").asString());
      statement.setString(13, metadata.path("rubricVersion").asString());
      statement.setString(14, metadata.path("graderVersion").asString());
      statement.setString(15, selected.bundleId());
      statement.setString(16, selected.bundleDigest());
      statement.setString(17, selected.manifestDigest());
      statement.setBoolean(18, !metadata.path("mode").asString().equals("practice"));
      statement.executeUpdate();
    }
  }

  private void insertJournal(Connection connection, UUID owner, long epoch, UUID attempt,
      UUID operation, AllocationPublication.Candidate selected) throws SQLException {
    JsonNode metadata = selected.allocation();
    var record = new LinkedHashMap<String, Object>();
    record.put("schemaVersion", 1);
    record.put("type", "EXPOSURE_FLOOR");
    record.put("operationId", operation.toString());
    record.put("attemptId", attempt.toString());
    record.put("userId", owner.toString());
    record.put("learningEpoch", Long.toString(epoch));
    for (String field : new String[]{"familyId", "exposureGroup", "equivalenceKey", "caseVersion",
        "rubricVersion", "graderVersion"}) {
      record.put(field, metadata.path(field).asString());
    }
    record.put("bundleId", selected.bundleId());
    record.put("bundleSha256", selected.bundleDigest());
    record.put("manifestSha256", selected.manifestDigest());
    record.put("level", "encounter");
    byte[] bytes = WireCodec.JSON.writeValueAsBytes(record);
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.allocation_journal(operation_id,attempt_id,user_id,learning_epoch,"
            + "namespace,record_bytes,record_sha256) VALUES (?,?,?,?,?,?,?)")) {
      statement.setObject(1, operation);
      statement.setObject(2, attempt);
      statement.setObject(3, owner);
      statement.setLong(4, epoch);
      statement.setString(5, namespace);
      statement.setBytes(6, bytes);
      statement.setString(7, AllocationWire.sha(bytes));
      statement.executeUpdate();
    }
  }

  private static void saveReceipt(Connection connection, UUID owner, long epoch, UUID key,
      String fingerprint, UUID attempt) throws SQLException {
    try (var statement = connection.prepareStatement(
        "INSERT INTO learning.allocation_receipt(user_id,idempotency_key,fingerprint,"
            + "attempt_id,learning_epoch) VALUES (?,?,?,?,?)")) {
      statement.setObject(1, owner);
      statement.setObject(2, key);
      statement.setString(3, fingerprint);
      statement.setObject(4, attempt);
      statement.setLong(5, epoch);
      statement.executeUpdate();
    }
  }

  private static Attempt requireCurrent(Connection connection, UUID owner, long epoch, UUID attempt)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT user_id,learning_epoch,draft_id,operation_id,bundle_id,bundle_sha256,"
            + "manifest_sha256,state,independent_at_assignment "
            + "FROM learning.allocation_attempt WHERE attempt_id=? AND user_id=? FOR UPDATE")) {
      statement.setObject(1, attempt);
      statement.setObject(2, owner);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) throw AllocationWire.deny(404, "RESOURCE_NOT_FOUND");
        AdmissionGate.requireOwner(owner, rows.getObject(1, UUID.class));
        if (rows.getLong(2) != epoch) throw AllocationWire.deny(409, "LEARNING_EPOCH_CHANGED");
        return new Attempt(rows.getObject(3, UUID.class), rows.getObject(4, UUID.class),
            rows.getString(5), rows.getString(6), rows.getString(7), rows.getString(8),
            rows.getBoolean(9));
      }
    }
  }

  private static boolean journalVerified(Connection connection, UUID operation, UUID owner,
      UUID attempt, long epoch) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT 1 FROM learning.allocation_journal WHERE operation_id=? AND user_id=? "
            + "AND attempt_id=? AND learning_epoch=? AND state='VERIFIED' AND receipt IS NOT NULL")) {
      statement.setObject(1, operation);
      statement.setObject(2, owner);
      statement.setObject(3, attempt);
      statement.setLong(4, epoch);
      try (var rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  private record Attempt(UUID draft, UUID operation, String bundleId, String bundleDigest,
      String manifestDigest, String state, boolean independent) {}
}
