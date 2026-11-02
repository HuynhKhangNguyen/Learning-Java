package lab.backend.content.browser;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import lab.backend.content.publication.PublicationDigest;
import lab.backend.content.transport.WireCodec;

/**
 * Đọc canonical head, Learning readiness và public DTO bytes trong một MVCC snapshot.
 *
 * <p>Input là connection của Content DB; output chỉ được tạo khi FENCE đã gắn đúng
 * publication operation với snapshot bất biến. Lỗi DB, thiếu binding hoặc digest
 * sai đều fail closed và không làm thay đổi dữ liệu.
 */
class BrowserStore {
  record View(long canonical, long applied, java.time.Instant asOf, BrowserSnapshot snapshot) {}

  private final DataSource source;

  BrowserStore(DataSource source) {
    this.source = source;
  }

  View read() {
    try (Connection connection = source.getConnection()) {
      connection.setAutoCommit(false);
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      try {
        View view = read(connection);
        connection.commit();
        return view;
      } catch (RuntimeException | SQLException failure) {
        try {
          connection.rollback();
        } catch (SQLException ignored) {
          // Giữ lỗi bounded ban đầu; rollback failure không được làm lộ SQL detail.
        }
        throw failure;
      }
    } catch (WireCodec.Denied denied) {
      throw denied;
    } catch (SQLException failure) {
      throw denied("BROWSER_DATABASE_UNAVAILABLE");
    }
  }

  View read(Connection connection) throws SQLException {
    // Head/readiness/projection thuộc cùng MVCC snapshot. Với API-C03, connection
    // này còn nằm trong local fence lock của ContentAccess cho tới lúc commit.
    long canonical;
    long applied;
    java.time.Instant asOf;
    byte[] bytes;
    String snapshotDigest;
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT head.revision,environment.learning_ready_revision,clock_timestamp(),"
                    + "snapshot.snapshot_bytes,snapshot.snapshot_sha256 "
                    + "FROM content.publication_canonical_head head "
                    + "CROSS JOIN content.publication_environment environment "
                    + "JOIN content.browser_canonical_snapshot binding "
                    + "ON binding.revision=head.revision "
                    + "AND binding.bundle_id=head.bundle_id "
                    + "AND binding.bundle_digest=head.bundle_digest "
                    + "JOIN content.browser_snapshot snapshot "
                    + "ON snapshot.operation_id=binding.operation_id "
                    + "AND snapshot.revision=binding.revision "
                    + "AND snapshot.bundle_id=binding.bundle_id "
                    + "AND snapshot.bundle_digest=binding.bundle_digest "
                    + "AND snapshot.schema_version=1 "
                    + "WHERE head.singleton AND environment.singleton")) {
      if (!result.next()) {
        throw denied("BROWSER_SNAPSHOT_MISSING");
      }
      canonical = result.getLong(1);
      applied = result.getLong(2);
      asOf = result.getObject(3, OffsetDateTime.class).toInstant();
      bytes = result.getBytes(4);
      snapshotDigest = result.getString(5);
      if (result.next() || applied > canonical) {
        throw denied("BROWSER_HEAD_INVALID");
      }
    }
    if (!MessageDigest.isEqual(
        PublicationDigest.sha256(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        snapshotDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
      throw denied("BROWSER_SNAPSHOT_CORRUPT");
    }
    return new View(canonical, applied, asOf, BrowserSnapshot.parse(bytes));
  }

  void verifyRuntime() {
    try (Connection connection = source.getConnection();
        var statement = connection.createStatement();
        var result =
            statement.executeQuery(
                "SELECT current_database(),current_user,"
                    + "has_table_privilege(current_user,'content.browser_snapshot','SELECT'),"
                    + "has_table_privilege(current_user,'content.browser_snapshot','INSERT'),"
                    + "has_table_privilege(current_user,'content.browser_snapshot','UPDATE'),"
                    + "has_table_privilege(current_user,'content.browser_snapshot','DELETE'),"
                    + "has_table_privilege(current_user,"
                    + "'content.browser_canonical_snapshot','SELECT'),"
                    + "has_table_privilege(current_user,"
                    + "'content.browser_canonical_snapshot','INSERT')")) {
      if (!result.next()
          || !"content_db".equals(result.getString(1))
          || !"content_app".equals(result.getString(2))
          || !result.getBoolean(3)
          || result.getBoolean(4)
          || result.getBoolean(5)
          || result.getBoolean(6)
          || !result.getBoolean(7)
          || result.getBoolean(8)) {
        throw denied("BROWSER_NOT_READY");
      }
    } catch (SQLException failure) {
      throw denied("BROWSER_NOT_READY");
    }
  }

  private static WireCodec.Denied denied(String code) {
    return new WireCodec.Denied(503, code);
  }
}
