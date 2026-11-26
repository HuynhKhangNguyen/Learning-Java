package lab.backend.learning.work;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lab.backend.learning.transport.WireCodec;
import tools.jackson.databind.JsonNode;

/** Đọc immutable released pool dưới shared DB publication lock, tương thích exclusive INT04. */
final class AllocationPublication {
  record Candidate(String bundleId, String bundleDigest, String manifestDigest,
      JsonNode bundle, JsonNode allocation) {}

  private final JsonNode currentBundle;
  private final JsonNode currentManifest;

  private AllocationPublication(JsonNode bundle, JsonNode manifest) {
    currentBundle = bundle;
    currentManifest = manifest;
  }

  static AllocationPublication lock(
      Connection connection,
      UUID realm,
      UUID generation) throws SQLException {
    try (var statement = connection.createStatement()) {
      // Cùng advisory key với PublicationStore.phase; lock sống đến commit/rollback,
      // không chỉ là process-local gate nên hai replica vẫn có một linearization.
      statement.execute("SELECT pg_advisory_xact_lock_shared(7406,1)");
    }
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT e.ready,h.fence_operation,r.bundle_bytes,r.manifest_bytes,e.realm_id,e.db_generation "
                + "FROM learning.publication_environment e "
                + "JOIN learning.publication_head h ON h.singleton=e.singleton "
                + "LEFT JOIN learning.content_replica r ON r.bundle_id=h.active_bundle_id "
                + "WHERE e.singleton")) {
      if (!rows.next() || !rows.getBoolean(1) || rows.getObject(2) != null
          || !realm.equals(rows.getObject(5, UUID.class))
          || !generation.equals(rows.getObject(6, UUID.class))) {
        throw AllocationWire.deny(503, "PUBLICATION_PENDING");
      }
      if (rows.getBytes(3) == null) {
        throw AllocationWire.deny(503, "ALLOCATION_CONTENT_UNAVAILABLE");
      }
      return new AllocationPublication(WireCodec.JSON.readTree(rows.getBytes(3)),
          WireCodec.JSON.readTree(rows.getBytes(4)));
    }
  }

  List<Candidate> pool(Connection connection, AllocationWire.Create request) throws SQLException {
    List<Candidate> candidates = new ArrayList<>();
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT DISTINCT r.bundle_id,r.bundle_sha256,r.manifest_sha256,r.bundle_bytes "
                + "FROM learning.content_replica r JOIN learning.publication_operation o "
                + "ON o.bundle_id=r.bundle_id WHERE o.state='RELEASED' ORDER BY r.bundle_id")) {
      while (rows.next()) {
        JsonNode bundle = WireCodec.JSON.readTree(rows.getBytes(4));
        if (bundle.path("schemaVersion").asInt() != 3) {
          continue;
        }
        JsonNode metadata = bundle.path("allocation");
        if (!metadata.path("exerciseId").asString().equals(request.exerciseId())
            || !metadata.path("mode").asString().equals(request.requestedMode())) {
          continue;
        }
        Candidate candidate = new Candidate(rows.getString(1), rows.getString(2),
            rows.getString(3), bundle, metadata);
        if (allowed(candidate)) {
          candidates.add(candidate);
        }
      }
    }
    return candidates;
  }

  Candidate pinned(Connection connection, String bundleId) throws SQLException {
    try (var statement = connection.prepareStatement(
        "SELECT r.bundle_sha256,r.manifest_sha256,r.bundle_bytes "
            + "FROM learning.content_replica r WHERE bundle_id=? AND EXISTS "
            + "(SELECT 1 FROM learning.publication_operation o "
            + "WHERE o.bundle_id=r.bundle_id AND o.state='RELEASED')")) {
      statement.setString(1, bundleId);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw AllocationWire.deny(503, "ALLOCATION_CONTENT_UNAVAILABLE");
        }
        JsonNode bundle = WireCodec.JSON.readTree(rows.getBytes(3));
        if (bundle.path("schemaVersion").asInt() != 3) {
          throw AllocationWire.deny(503, "ALLOCATION_CONTENT_UNAVAILABLE");
        }
        Candidate candidate = new Candidate(bundleId, rows.getString(1), rows.getString(2),
            bundle, bundle.path("allocation"));
        if (!allowed(candidate)) {
          throw AllocationWire.deny(409, "ALLOCATION_WITHDRAWN");
        }
        return candidate;
      }
    }
  }

  private boolean allowed(Candidate candidate) {
    if (currentBundle.path("schemaVersion").asInt() != 3) {
      return false;
    }
    String version = candidate.allocation.path("rubricVersion").asString();
    for (JsonNode target : candidate.bundle.path("rubricTargets")) {
      boolean found = false;
      for (JsonNode policy : currentManifest) {
        if (policy.path("capabilityId").equals(target)
            && policy.path("version").asString().equals(version)) {
          if (!policy.path("newAttemptAllowed").asBoolean()
              || !policy.path("bodyAvailable").asBoolean()) {
            return false;
          }
          found = true;
        }
      }
      // Manifest chỉ liệt kê old versions. Absence chỉ được coi là current
      // khi active v3 cùng target và cùng rubricVersion, không đoán từ số version.
      if (!found && (!version.equals(currentBundle.path("allocation")
          .path("rubricVersion").asString()) || !containsTarget(currentBundle, target))) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsTarget(JsonNode bundle, JsonNode target) {
    for (JsonNode existing : bundle.path("rubricTargets")) {
      if (existing.equals(target)) {
        return true;
      }
    }
    return false;
  }
}
