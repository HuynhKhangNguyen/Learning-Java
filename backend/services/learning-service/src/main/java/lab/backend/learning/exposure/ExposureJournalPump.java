package lab.backend.learning.exposure;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lab.backend.learning.journal.JournalStore;
import lab.backend.learning.transport.WireCodec;
import lab.backend.learning.work.AllocationWire;
import tools.jackson.databind.JsonNode;

/**
 * Durable outbox pump: đọc pending bytes rồi đóng DB connection trước mọi journal I/O.
 * Chỉ xác nhận receipt; không có prompt/public DTO và không tự cấp disclosure rights.
 */
public final class ExposureJournalPump {
  private final DataSource source;
  private final JournalStore journal;

  public ExposureJournalPump(DataSource source, JournalStore journal) {
    this.source = source;
    this.journal = journal;
  }

  public boolean verify(UUID attempt) {
    try {
      Pending pending = load(attempt);
      if (pending == null || !AllocationWire.sha(pending.bytes).equals(pending.digest)) {
        return false;
      }
      JournalStore.Receipt receipt = pending.receipt == null
          ? journal.append(pending.namespace, pending.operation.toString(), pending.bytes)
          : decode(pending.receipt);
      if (!receipt.namespace().equals(pending.namespace)
          || !receipt.recordId().equals(pending.operation.toString())
          || !receipt.recordDigest().equals(pending.digest)
          || receipt.sequence() < 1) {
        return false;
      }
      var inventory = journal.inventory(pending.namespace, receipt.verifiedHeadDigest());
      if (!inventory.fixedHeadDigest().equals(receipt.verifiedHeadDigest())) {
        return false;
      }
      boolean covered = inventory.entries().stream().anyMatch(entry ->
          entry.sequence() == receipt.sequence() && entry.recordId().equals(receipt.recordId())
              && entry.recordDigest().equals(receipt.recordDigest())
              && entry.nodeDigest().equals(receipt.nodeDigest()));
      if (!covered) {
        return false;
      }
      if (pending.receipt == null) {
        acknowledge(pending, receipt);
      }
      return true;
    } catch (Exception unavailable) {
      // Timeout/conflict/corrupt/missing prefix đều giữ preparing. Không log bytes/record.
      return false;
    }
  }

  private Pending load(UUID attempt) throws SQLException {
    try (var connection = source.getConnection();
        var statement = connection.prepareStatement(
            "SELECT j.operation_id,j.namespace,j.record_bytes,j.record_sha256,j.receipt::text,"
                + "a.user_id,a.learning_epoch,a.family_id,a.exposure_group,a.equivalence_key,"
                + "a.case_version,a.rubric_version,a.grader_version,a.bundle_id,a.bundle_sha256,"
                + "a.manifest_sha256 FROM learning.allocation_journal j "
                + "JOIN learning.allocation_attempt a ON a.attempt_id=j.attempt_id "
                + "AND a.operation_id=j.operation_id AND a.user_id=j.user_id "
                + "AND a.learning_epoch=j.learning_epoch WHERE j.attempt_id=?")) {
      statement.setObject(1, attempt);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) {
          return null;
        }
        JsonNode record = WireCodec.strict(rows.getBytes(3));
        if (!record.path("operationId").asString().equals(rows.getObject(1, UUID.class).toString())
            || !record.path("attemptId").asString().equals(attempt.toString())
            || !record.path("userId").asString().equals(rows.getObject(6, UUID.class).toString())
            || !record.path("learningEpoch").asString().equals(Long.toString(rows.getLong(7)))) {
          return null;
        }
        String[] fields = {"familyId", "exposureGroup", "equivalenceKey", "caseVersion",
            "rubricVersion", "graderVersion", "bundleId", "bundleSha256", "manifestSha256"};
        for (int index = 0; index < fields.length; index++) {
          if (!record.path(fields[index]).asString().equals(rows.getString(index + 8))) {
            return null;
          }
        }
        return new Pending(rows.getObject(1, UUID.class), rows.getString(2), rows.getBytes(3),
            rows.getString(4), rows.getString(5));
      }
    }
  }

  private void acknowledge(Pending pending, JournalStore.Receipt receipt) throws SQLException {
    byte[] bytes = WireCodec.JSON.writeValueAsBytes(Map.ofEntries(
        Map.entry("namespace", receipt.namespace()), Map.entry("recordId", receipt.recordId()),
        Map.entry("recordDigest", receipt.recordDigest()),
        Map.entry("sequence", Long.toString(receipt.sequence())),
        Map.entry("nodeDigest", receipt.nodeDigest()),
        Map.entry("verifiedHeadDigest", receipt.verifiedHeadDigest()),
        Map.entry("casRetries", receipt.casRetries())));
    try (var connection = source.getConnection();
        var statement = connection.prepareStatement(
            "UPDATE learning.allocation_journal SET state='VERIFIED',receipt=?::jsonb "
                + "WHERE operation_id=? AND record_sha256=? AND state='PENDING'")) {
      statement.setString(1, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
      statement.setObject(2, pending.operation);
      statement.setString(3, pending.digest);
      statement.executeUpdate();
    }
  }

  private static JournalStore.Receipt decode(String stored) {
    JsonNode node = WireCodec.JSON.readTree(stored);
    if (!node.isObject() || !node.propertyNames().equals(java.util.Set.of(
        "namespace", "recordId", "recordDigest", "sequence", "nodeDigest",
        "verifiedHeadDigest", "casRetries"))) {
      throw new IllegalArgumentException();
    }
    return new JournalStore.Receipt(
        WireCodec.string(node, "namespace"), WireCodec.string(node, "recordId"),
        WireCodec.string(node, "recordDigest"), Long.parseLong(WireCodec.decimal(node.get("sequence"))),
        WireCodec.string(node, "nodeDigest"), WireCodec.string(node, "verifiedHeadDigest"),
        node.path("casRetries").asInt());
  }

  private record Pending(UUID operation, String namespace, byte[] bytes, String digest,
      String receipt) {}
}
