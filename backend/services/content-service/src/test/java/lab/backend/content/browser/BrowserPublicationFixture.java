package lab.backend.content.browser;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import lab.backend.content.publication.PublicationDigest;
import lab.backend.content.transport.WireCodec;

/**
 * Dựng publication operation tối thiểu cho browser DB/runtime test.
 *
 * <p>Input là snapshot public và Content connection thuộc disposable lab; output
 * là operation đang ở `FENCE_PENDING` có candidate commitment cùng STAGE/FENCE
 * receipt. Helper chỉ ghi fixture test và không thay thế production coordinator.
 */
final class BrowserPublicationFixture {
  record Prepared(
      UUID operationId,
      long parentRevision,
      long targetRevision,
      String bundleId,
      String bundleDigest,
      byte[] snapshot,
      String snapshotDigest) {
    Prepared {
      snapshot = snapshot.clone();
    }

    @Override
    public byte[] snapshot() {
      return snapshot.clone();
    }
  }

  static void ensureBaseline(
      Connection connection,
      UUID realm,
      UUID producerGeneration,
      UUID participantGeneration,
      byte[] capability)
      throws SQLException {
    connection.setAutoCommit(false);
    // CMD-03 chạy browser phase trên cùng disposable DB sau publication suites.
    // Thu hồi test-only operation còn active để fixture này có một writer rõ ràng;
    // production coordinator không dùng helper và không được sửa state trực tiếp.
    connection
        .createStatement()
        .executeUpdate(
            "UPDATE content.publication_operation operation "
                + "SET state=CASE WHEN operation.target_revision <= head.revision "
                + "THEN 'COMPLETE' ELSE 'ABORTED' END,completed_at=clock_timestamp() "
                + "FROM content.publication_canonical_head head "
                + "WHERE head.singleton AND operation.state NOT IN ('COMPLETE','ABORTED')");
    connection
        .createStatement()
        .executeUpdate(
            "UPDATE content.publication_candidate_reservation "
                + "SET operation_id=NULL,generation=generation+1 "
                + "WHERE singleton AND operation_id IS NOT NULL");
    String bundleId = "r07-browser-baseline";
    byte[] bundle = "browser-baseline-private-placeholder".getBytes(StandardCharsets.UTF_8);
    String bundleDigest = PublicationDigest.sha256(bundle);
    byte[] manifest = "{}".getBytes(StandardCharsets.UTF_8);
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_bundle("
                + "bundle_id,target_revision,bundle_bytes,bundle_digest,"
                + "manifest_bytes,manifest_digest,validated) "
                + "VALUES (?,1,?,?,?,?,true) ON CONFLICT DO NOTHING")) {
      statement.setString(1, bundleId);
      statement.setBytes(2, bundle);
      statement.setString(3, bundleDigest);
      statement.setBytes(4, manifest);
      statement.setString(5, PublicationDigest.sha256(manifest));
      statement.executeUpdate();
    }
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_canonical_head("
                + "singleton,revision,bundle_id,bundle_digest) "
                + "VALUES (true,1,?,?) ON CONFLICT DO NOTHING")) {
      statement.setString(1, bundleId);
      statement.setString(2, bundleDigest);
      statement.executeUpdate();
    }
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_environment("
                + "singleton,realm_id,producer_generation,participant_generation,"
                + "coordinator_capability_sha256,learning_ready_revision) "
                + "VALUES (true,?,?,?,sha256(?),1) ON CONFLICT DO NOTHING")) {
      statement.setObject(1, realm);
      statement.setObject(2, producerGeneration);
      statement.setObject(3, participantGeneration);
      statement.setBytes(4, capability);
      statement.executeUpdate();
    }
    connection.commit();
  }

  static Prepared prepare(Connection connection, byte[] snapshot, String prefix)
      throws Exception {
    connection.setAutoCommit(false);
    long parent;
    String realm;
    String producer;
    String participant;
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT head.revision,environment.realm_id,"
                    + "environment.producer_generation,environment.participant_generation "
                    + "FROM content.publication_canonical_head head "
                    + "CROSS JOIN content.publication_environment environment "
                    + "WHERE head.singleton AND environment.singleton FOR UPDATE")) {
      if (!result.next()) {
        throw new IllegalStateException("Browser publication baseline is missing");
      }
      parent = result.getLong(1);
      realm = result.getString(2);
      producer = result.getString(3);
      participant = result.getString(4);
    }
    long target = parent + 1;
    UUID operationId = UUID.randomUUID();
    String bundleId = prefix + "-" + target + "-" + operationId.toString().substring(0, 8);
    byte[] bundle = ("private-" + bundleId).getBytes(StandardCharsets.UTF_8);
    String bundleDigest = PublicationDigest.sha256(bundle);
    byte[] manifest = "{}".getBytes(StandardCharsets.UTF_8);
    String manifestDigest = PublicationDigest.sha256(manifest);
    String snapshotDigest = PublicationDigest.sha256(snapshot);
    byte[] candidate =
        candidate(operationId, parent, target, bundleId, bundleDigest, snapshotDigest);
    long generation;
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT generation FROM content.publication_candidate_reservation "
                    + "WHERE singleton")) {
      result.next();
      generation = result.getLong(1);
    }

    insertBundle(connection, target, bundleId, bundle, bundleDigest, manifest, manifestDigest);
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_candidate("
                + "operation_id,reservation_generation,parent_revision,target_revision,"
                + "bundle_id,candidate_digest,candidate_bytes) VALUES (?,?,?,?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setLong(2, generation);
      statement.setLong(3, parent);
      statement.setLong(4, target);
      statement.setString(5, bundleId);
      statement.setString(6, PublicationDigest.sha256(candidate));
      statement.setBytes(7, candidate);
      statement.executeUpdate();
    }
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_operation("
                + "operation_id,reservation_generation,parent_revision,target_revision,"
                + "bundle_id,bundle_sha256,manifest_sha256,realm_id,producer_generation,"
                + "participant_generation,state,last_sequence) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,'FENCE_PENDING',?)")) {
      statement.setObject(1, operationId);
      statement.setLong(2, generation);
      statement.setLong(3, parent);
      statement.setLong(4, target);
      statement.setString(5, bundleId);
      statement.setString(6, bundleDigest);
      statement.setString(7, manifestDigest);
      statement.setObject(8, UUID.fromString(realm));
      statement.setObject(9, UUID.fromString(producer));
      statement.setObject(10, UUID.fromString(participant));
      statement.setLong(11, nextSequence(connection) + 1);
      statement.executeUpdate();
    }
    insertReceiptPair(connection, operationId);
    connection.commit();
    return new Prepared(
        operationId, parent, target, bundleId, bundleDigest, snapshot, snapshotDigest);
  }

  static void install(Connection connection, Prepared fixture) throws SQLException {
    connection.setAutoCommit(false);
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.browser_snapshot("
                + "operation_id,revision,bundle_id,bundle_digest,schema_version,"
                + "snapshot_bytes,snapshot_sha256) VALUES (?,?,?,?,1,?,?)")) {
      statement.setObject(1, fixture.operationId());
      statement.setLong(2, fixture.targetRevision());
      statement.setString(3, fixture.bundleId());
      statement.setString(4, fixture.bundleDigest());
      statement.setBytes(5, fixture.snapshot());
      statement.setString(6, fixture.snapshotDigest());
      statement.executeUpdate();
    }
    connection.commit();
  }

  static void fence(Connection connection, Prepared fixture) throws SQLException {
    connection.setAutoCommit(false);
    try (var statement =
        connection.prepareStatement(
            "UPDATE content.publication_canonical_head "
                + "SET revision=?,bundle_id=?,bundle_digest=? WHERE singleton AND revision=?")) {
      statement.setLong(1, fixture.targetRevision());
      statement.setString(2, fixture.bundleId());
      statement.setString(3, fixture.bundleDigest());
      statement.setLong(4, fixture.parentRevision());
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Test canonical head conflict");
      }
    }
    try (var statement =
        connection.prepareStatement(
            "UPDATE content.publication_operation "
                + "SET state='COMPLETE',completed_at=clock_timestamp() WHERE operation_id=?")) {
      statement.setObject(1, fixture.operationId());
      statement.executeUpdate();
    }
    connection.commit();
  }

  static Prepared publish(Connection connection, byte[] snapshot, String prefix) throws Exception {
    Prepared fixture = prepare(connection, snapshot, prefix);
    install(connection, fixture);
    fence(connection, fixture);
    return fixture;
  }

  private static byte[] candidate(
      UUID operationId,
      long parent,
      long target,
      String bundleId,
      String bundleDigest,
      String snapshotDigest)
      throws Exception {
    var candidate = WireCodec.JSON.createObjectNode();
    candidate.put("operationId", operationId.toString());
    candidate.put("parentRevision", parent);
    candidate.put("targetRevision", target);
    candidate.putObject("bundle").put("id", bundleId).put("sha256", bundleDigest);
    candidate
        .putArray("components")
        .addObject()
        .put("id", "PUBLIC-BROWSER-SNAPSHOT")
        .put("type", "catalog")
        .put("version", "1")
        .put("sha256", snapshotDigest);
    return WireCodec.JSON.writeValueAsBytes(candidate);
  }

  private static void insertBundle(
      Connection connection,
      long revision,
      String bundleId,
      byte[] bundle,
      String bundleDigest,
      byte[] manifest,
      String manifestDigest)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO content.publication_bundle("
                + "bundle_id,target_revision,bundle_bytes,bundle_digest,"
                + "manifest_bytes,manifest_digest,validated) VALUES (?,?,?,?,?,?,true)")) {
      statement.setString(1, bundleId);
      statement.setLong(2, revision);
      statement.setBytes(3, bundle);
      statement.setString(4, bundleDigest);
      statement.setBytes(5, manifest);
      statement.setString(6, manifestDigest);
      statement.executeUpdate();
    }
  }

  private static long nextSequence(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT COALESCE(max(delivery_sequence),0)+1 "
                    + "FROM content.publication_phase_outbox")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void insertReceiptPair(Connection connection, UUID operationId)
      throws SQLException {
    long sequence = nextSequence(connection);
    for (String phase : new String[] {"STAGE", "FENCE"}) {
      byte[] command = ("{\"phase\":\"" + phase + "\"}").getBytes(StandardCharsets.UTF_8);
      String commandDigest = PublicationDigest.sha256(command);
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO content.publication_phase_outbox("
                  + "operation_id,phase,delivery_sequence,command_bytes,command_sha256,delivered) "
                  + "VALUES (?,?,?,?,?,true)")) {
        statement.setObject(1, operationId);
        statement.setString(2, phase);
        statement.setLong(3, sequence++);
        statement.setBytes(4, command);
        statement.setString(5, commandDigest);
        statement.executeUpdate();
      }
      byte[] receipt = ("{\"state\":\"" + phase + "D\"}").getBytes(StandardCharsets.UTF_8);
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO content.publication_phase_receipt("
                  + "operation_id,phase,receipt_bytes,receipt_sha256,command_sha256) "
                  + "VALUES (?,?,?,?,?)")) {
        statement.setObject(1, operationId);
        statement.setString(2, phase);
        statement.setBytes(3, receipt);
        statement.setString(4, PublicationDigest.sha256(receipt));
        statement.setString(5, commandDigest);
        statement.executeUpdate();
      }
    }
  }

  private BrowserPublicationFixture() {}
}
