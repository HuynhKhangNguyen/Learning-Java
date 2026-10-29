package lab.backend.content.publication;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Lưu bundle/candidate trong một transaction và giữ một active reservation.
 * Exact retry hội tụ; operation khác không thể cùng active; mọi lỗi rollback cả
 * bundle, candidate và claim nên canonical good head không bị ảnh hưởng.
 */
public final class PublicationStore {
  private final DataSource source;
  private final byte[] coordinatorCapability;

  public PublicationStore(DataSource source) {
    this(source, null);
  }

  public PublicationStore(DataSource source, byte[] coordinatorCapability) {
    this.source = source;
    this.coordinatorCapability = coordinatorCapability == null
        ? null
        : coordinatorCapability.clone();
  }

  public void prepare(CandidateValidator.Valid candidate) throws SQLException {
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try {
        lockAndCheckGoodHead(connection, candidate.parent());
        Reservation reservation = reservation(connection);
        StoredCandidate existing = candidate(connection, candidate.operationId());

        // Candidate của generation đã kết thúc không được chiếm slot lại khi
        // retry cũ tới sau abort/commit. Operation mới có UUID mới và dùng
        // generation hiện hành.
        if (existing != null && existing.generation() != reservation.generation()) {
          throw conflict("PUBLICATION_STALE_OPERATION");
        }
        if (reservation.operationId() != null
            && !reservation.operationId().equals(candidate.operationId())) {
          throw conflict("PUBLICATION_ACTIVE_CONFLICT");
        }

        insertOrVerifyBundle(connection, candidate);
        insertCandidate(connection, candidate, reservation.generation());
        claimReservation(connection, candidate.operationId(), reservation.generation());
        verifyCandidate(connection, candidate, reservation.generation());
        connection.commit();
      } catch (Exception failure) {
        connection.rollback();
        if (failure instanceof SQLException sqlFailure) throw sqlFailure;
        throw new SQLException("PREPARE_FAILED", failure);
      }
    }
  }

  private static void lockAndCheckGoodHead(Connection connection, long expectedParent)
      throws SQLException {
    // Shared head lock serialize với canonical writer tương lai nhưng không bị
    // giữ qua network. R06a chỉ đọc head và không có đường advance revision.
    try (var statement = connection.prepareStatement(
        "SELECT revision FROM content.publication_canonical_head WHERE singleton FOR SHARE");
        var result = statement.executeQuery()) {
      if (!result.next() || result.getLong(1) != expectedParent) {
        throw conflict("PUBLICATION_PARENT_CONFLICT");
      }
    }
  }

  private static Reservation reservation(Connection connection) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT generation,operation_id FROM content.publication_candidate_reservation WHERE singleton");
        var result = statement.executeQuery()) {
      if (!result.next()) throw conflict("PUBLICATION_RESERVATION_MISSING");
      return new Reservation(result.getLong(1), result.getObject(2, UUID.class));
    }
  }

  private static StoredCandidate candidate(Connection connection, UUID operationId)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT reservation_generation,parent_revision,target_revision,bundle_id,candidate_digest,candidate_bytes "
            + "FROM content.publication_candidate WHERE operation_id=?")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        return result.next() ? storedCandidate(result) : null;
      }
    }
  }

  private static void insertOrVerifyBundle(
      Connection connection, CandidateValidator.Valid candidate) throws SQLException {
    String manifestDigest = PublicationDigest.sha256(candidate.manifestBytes());
    try (var statement = connection.prepareStatement(
        "INSERT INTO content.publication_bundle("
            + "bundle_id,target_revision,bundle_bytes,bundle_digest,manifest_bytes,manifest_digest,validated) "
            + "VALUES(?,?,?,?,?,?,true) ON CONFLICT DO NOTHING")) {
      statement.setString(1, candidate.bundleId());
      statement.setLong(2, candidate.target());
      statement.setBytes(3, candidate.bundleBytes());
      statement.setString(4, candidate.bundleDigest());
      statement.setBytes(5, candidate.manifestBytes());
      statement.setString(6, manifestDigest);
      statement.executeUpdate();
    }
    try (var statement = connection.prepareStatement(
        "SELECT target_revision,bundle_digest,bundle_bytes,manifest_digest,manifest_bytes "
            + "FROM content.publication_bundle WHERE bundle_id=? AND validated")) {
      statement.setString(1, candidate.bundleId());
      try (var result = statement.executeQuery()) {
        if (!result.next()
            || result.getLong(1) != candidate.target()
            || !result.getString(2).equals(candidate.bundleDigest())
            || !Arrays.equals(result.getBytes(3), candidate.bundleBytes())
            || !result.getString(4).equals(manifestDigest)
            || !Arrays.equals(result.getBytes(5), candidate.manifestBytes())) {
          throw conflict("PUBLICATION_BUNDLE_CONFLICT");
        }
      }
    }
  }

  private static void insertCandidate(
      Connection connection, CandidateValidator.Valid candidate, long generation) throws SQLException {
    try (var statement = connection.prepareStatement(
        "INSERT INTO content.publication_candidate("
            + "operation_id,reservation_generation,parent_revision,target_revision,bundle_id,candidate_digest,candidate_bytes) "
            + "VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")) {
      statement.setObject(1, candidate.operationId());
      statement.setLong(2, generation);
      statement.setLong(3, candidate.parent());
      statement.setLong(4, candidate.target());
      statement.setString(5, candidate.bundleId());
      statement.setString(6, PublicationDigest.sha256(candidate.candidateBytes()));
      statement.setBytes(7, candidate.candidateBytes());
      statement.executeUpdate();
    }
  }

  private static void claimReservation(
      Connection connection, UUID operationId, long generation) throws SQLException {
    // SECURITY DEFINER function chỉ cho claim null->operation hoặc exact retry;
    // app role không có quyền clear/increment slot như coordinator tương lai.
    try (var statement = connection.prepareStatement(
        "SELECT content.claim_publication_candidate(?,?)")) {
      statement.setObject(1, operationId);
      statement.setLong(2, generation);
      statement.executeQuery().close();
    }
  }

  private static void verifyCandidate(
      Connection connection, CandidateValidator.Valid expected, long generation) throws SQLException {
    StoredCandidate actual = candidate(connection, expected.operationId());
    String digest = PublicationDigest.sha256(expected.candidateBytes());
    if (actual == null
        || actual.generation() != generation
        || actual.parent() != expected.parent()
        || actual.target() != expected.target()
        || !actual.bundleId().equals(expected.bundleId())
        || !actual.digest().equals(digest)
        || !Arrays.equals(actual.bytes(), expected.candidateBytes())) {
      throw conflict("PUBLICATION_CANDIDATE_CONFLICT");
    }
  }

  public Bundle get(String id) throws SQLException {
    try (Connection connection = source.getConnection();
        var statement = connection.prepareStatement(
            "SELECT target_revision,bundle_digest,bundle_bytes,manifest_digest,manifest_bytes "
                + "FROM content.publication_bundle WHERE bundle_id=? AND validated")) {
      statement.setString(1, id);
      try (var result = statement.executeQuery()) {
        if (!result.next()) return null;
        byte[] bytes = result.getBytes(3);
        byte[] manifest = result.getBytes(5);
        String bundleDigest = result.getString(2);
        String manifestDigest = result.getString(4);
        if (!PublicationDigest.sha256(bytes).equals(bundleDigest)
            || !PublicationDigest.sha256(manifest).equals(manifestDigest)) {
          throw new SQLException("STORED_PUBLICATION_DIGEST_MISMATCH");
        }
        return new Bundle(id, result.getLong(1), bundleDigest, bytes, manifestDigest, manifest);
      }
    }
  }

  /**
   * Mở coordinator từ candidate đã giữ reservation và ghi exact STAGE command
   * trong cùng transaction của trusted database transition.
   */
  public PublicationCoordinatorWire.Binding start(UUID operationId) throws SQLException {
    requireCoordinatorCapability();
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try {
        PublicationCoordinatorWire.Binding binding = binding(connection, operationId);
        long sequence = startSequence(connection, operationId);
        PublicationCoordinatorWire.Command command =
            PublicationCoordinatorWire.command(binding, "STAGE", sequence);
        callStart(connection, operationId, command);
        connection.commit();
        return binding;
      } catch (Exception failure) {
        connection.rollback();
        if (failure instanceof SQLException sqlFailure) {
          throw sqlFailure;
        }
        throw new SQLException("PUBLICATION_START_FAILED", failure);
      }
    }
  }

  /**
   * Receipt đã được strict codec xác minh trước khi vào đây. Database vẫn khóa
   * operation và tự kiểm prefix; FENCE ACK thực hiện PUB-04 atomically.
   */
  public void accept(
      UUID operationId,
      String phase,
      byte[] receiptBytes) throws SQLException {
    LeasedPending leased = lease(operationId);
    if (!leased.pending().phase().equals(phase)) {
      throw conflict("PUBLICATION_PHASE_CONFLICT");
    }
    accept(leased, receiptBytes);
  }

  public void accept(LeasedPending leased, byte[] receiptBytes) throws SQLException {
    requireCoordinatorCapability();
    UUID operationId = leased.pending().operationId();
    String phase = leased.pending().phase();
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try {
        PublicationCoordinatorWire.Binding binding = binding(connection, operationId);
        StoredOutbox current = outbox(connection, operationId, phase);
        PublicationCoordinatorWire.Command command = new PublicationCoordinatorWire.Command(
            phase,
            current.sequence(),
            current.bytes(),
            current.digest());
        PublicationCoordinatorWire.verify(receiptBytes, binding, command);
        boolean abortRequested = abortRequested(connection, operationId);
        PublicationCoordinatorWire.Command next =
            next(binding, phase, current.sequence(), abortRequested);
        callAccept(connection, operationId, phase, receiptBytes, next, leased.leaseToken());
        connection.commit();
      } catch (Exception failure) {
        connection.rollback();
        if (failure instanceof SQLException sqlFailure) {
          throw sqlFailure;
        }
        throw new SQLException("PUBLICATION_RECEIPT_REJECTED", failure);
      }
    }
  }

  public LeasedPending lease(UUID operationId) throws SQLException {
    requireCoordinatorCapability();
    Pending pending = pending(operationId);
    if (pending == null) {
      throw conflict("PUBLICATION_PENDING_DELIVERY_MISSING");
    }
    UUID token = UUID.randomUUID();
    try (Connection connection = source.getConnection();
        var statement = connection.prepareStatement(
            "SELECT content.claim_publication_delivery(?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setString(2, pending.phase());
      statement.setObject(3, token);
      statement.setBytes(4, coordinatorCapability);
      statement.executeQuery().close();
    }
    return new LeasedPending(pending, token);
  }

  /** ABORT chỉ được tạo trước PUB-04; SQL transition từ chối mọi state sau FENCE ACK. */
  public void requestAbort(UUID operationId) throws SQLException {
    requireCoordinatorCapability();
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (var statement = connection.prepareStatement(
            "SELECT content.request_publication_abort(?,?)")) {
          statement.setObject(1, operationId);
          statement.setBytes(2, coordinatorCapability);
          statement.executeQuery().close();
        }
        connection.commit();
      } catch (Exception failure) {
        connection.rollback();
        if (failure instanceof SQLException sqlFailure) {
          throw sqlFailure;
        }
        throw new SQLException("PUBLICATION_ABORT_REJECTED", failure);
      }
    }
  }

  public Pending pending(UUID operationId) throws SQLException {
    try (Connection connection = source.getConnection();
        var statement = connection.prepareStatement(
            "SELECT operation.state,outbox.phase,outbox.delivery_sequence,"
                + "outbox.command_bytes,outbox.command_sha256 "
                + "FROM content.publication_operation operation "
                + "JOIN content.publication_phase_outbox outbox "
                + "ON outbox.operation_id=operation.operation_id "
                + "WHERE operation.operation_id=? AND NOT outbox.delivered "
                + "ORDER BY outbox.delivery_sequence DESC LIMIT 1")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          return null;
        }
        return new Pending(
            operationId,
            result.getString(1),
            result.getString(2),
            result.getLong(3),
            result.getBytes(4),
            result.getString(5));
      }
    }
  }

  public Status status(UUID operationId) throws SQLException {
    try (Connection connection = source.getConnection();
        var statement = connection.prepareStatement(
            "SELECT state,parent_revision,target_revision,bundle_id,abort_requested "
                + "FROM content.publication_operation WHERE operation_id=?")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          return null;
        }
        return new Status(
            operationId,
            result.getString(1),
            result.getLong(2),
            result.getLong(3),
            result.getString(4),
            result.getBoolean(5));
      }
    }
  }

  private static PublicationCoordinatorWire.Binding binding(
      Connection connection,
      UUID operationId) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT candidate.parent_revision,candidate.target_revision,candidate.bundle_id,"
            + "bundle.bundle_digest,bundle.manifest_digest,environment.realm_id,"
            + "environment.producer_generation,environment.participant_generation "
            + "FROM content.publication_candidate candidate "
            + "JOIN content.publication_bundle bundle ON bundle.bundle_id=candidate.bundle_id "
            + "CROSS JOIN content.publication_environment environment "
            + "WHERE candidate.operation_id=? AND environment.singleton")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          throw conflict("PUBLICATION_BINDING_MISSING");
        }
        return new PublicationCoordinatorWire.Binding(
            operationId,
            result.getLong(1),
            result.getLong(2),
            result.getString(3),
            result.getString(4),
            result.getString(5),
            result.getObject(6, UUID.class),
            result.getObject(7, UUID.class),
            result.getObject(8, UUID.class));
      }
    }
  }

  private static long startSequence(Connection connection, UUID operationId)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT COALESCE((SELECT delivery_sequence "
            + "FROM content.publication_phase_outbox "
            + "WHERE operation_id=? AND phase='STAGE'), "
            + "last_delivery_sequence+1) "
            + "FROM content.publication_environment WHERE singleton")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          throw conflict("PUBLICATION_ENVIRONMENT_MISSING");
        }
        return result.getLong(1);
      }
    }
  }

  private static StoredOutbox outbox(
      Connection connection,
      UUID operationId,
      String phase) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT delivery_sequence,command_bytes,command_sha256 "
            + "FROM content.publication_phase_outbox "
            + "WHERE operation_id=? AND phase=?")) {
      statement.setObject(1, operationId);
      statement.setString(2, phase);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          throw conflict("PUBLICATION_OUTBOX_MISSING");
        }
        return new StoredOutbox(result.getLong(1), result.getBytes(2), result.getString(3));
      }
    }
  }

  private static boolean abortRequested(Connection connection, UUID operationId)
      throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT abort_requested FROM content.publication_operation WHERE operation_id=?")) {
      statement.setObject(1, operationId);
      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          throw conflict("PUBLICATION_OPERATION_MISSING");
        }
        return result.getBoolean(1);
      }
    }
  }

  private void callStart(
      Connection connection,
      UUID operationId,
      PublicationCoordinatorWire.Command command) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.start_publication(?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setBytes(2, command.bytes());
      statement.setString(3, command.digest());
      statement.setBytes(4, coordinatorCapability);
      statement.executeQuery().close();
    }
  }

  private void callAccept(
      Connection connection,
      UUID operationId,
      String phase,
      byte[] receiptBytes,
      PublicationCoordinatorWire.Command next,
      UUID leaseToken) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT content.accept_publication_receipt(?,?,?,?,?,?,?,?)")) {
      statement.setObject(1, operationId);
      statement.setString(2, phase);
      statement.setBytes(3, receiptBytes);
      statement.setString(4, PublicationDigest.sha256(receiptBytes));
      if (next == null) {
        statement.setNull(5, java.sql.Types.BINARY);
        statement.setNull(6, java.sql.Types.VARCHAR);
      } else {
        statement.setBytes(5, next.bytes());
        statement.setString(6, next.digest());
      }
      statement.setObject(7, leaseToken);
      statement.setBytes(8, coordinatorCapability);
      statement.executeQuery().close();
    }
  }

  private void requireCoordinatorCapability() {
    if (coordinatorCapability == null || coordinatorCapability.length < 32) {
      throw new IllegalStateException("PUBLICATION_COORDINATOR_CAPABILITY_MISSING");
    }
  }


  private static PublicationCoordinatorWire.Command next(
      PublicationCoordinatorWire.Binding binding,
      String phase,
      long sequence,
      boolean abortRequested) {
    return switch (phase) {
      case "STAGE" -> PublicationCoordinatorWire.command(
          binding,
          abortRequested ? "ABORT" : "FENCE",
          sequence + 1);
      case "FENCE" -> PublicationCoordinatorWire.command(
          binding,
          abortRequested ? "ABORT" : "RELEASE",
          sequence + 1);
      case "RELEASE", "ABORT" -> null;
      default -> throw new IllegalArgumentException("PUBLICATION_PHASE_INVALID");
    };
  }

  private static StoredCandidate storedCandidate(ResultSet result) throws SQLException {
    return new StoredCandidate(
        result.getLong(1), result.getLong(2), result.getLong(3), result.getString(4),
        result.getString(5), result.getBytes(6));
  }

  private static SQLException conflict(String code) {
    return new SQLException(code, "23505");
  }

  private record Reservation(long generation, UUID operationId) {}
  private record StoredCandidate(
      long generation, long parent, long target, String bundleId, String digest, byte[] bytes) {}

  private record StoredOutbox(long sequence, byte[] bytes, String digest) {
    private StoredOutbox {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  public record Pending(
      UUID operationId,
      String state,
      String phase,
      long sequence,
      byte[] commandBytes,
      String commandDigest) {
    public Pending {
      commandBytes = commandBytes.clone();
    }

    @Override
    public byte[] commandBytes() {
      return commandBytes.clone();
    }
  }

  public record LeasedPending(Pending pending, UUID leaseToken) {}

  public record Status(
      UUID operationId,
      String state,
      long parentRevision,
      long targetRevision,
      String bundleId,
      boolean abortRequested) {}

  public record Bundle(
      String id,
      long revision,
      String digest,
      byte[] bytes,
      String manifestDigest,
      byte[] manifest) {
    public Bundle {
      bytes = bytes.clone();
      manifest = manifest.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public byte[] manifest() { return manifest.clone(); }
  }
}
