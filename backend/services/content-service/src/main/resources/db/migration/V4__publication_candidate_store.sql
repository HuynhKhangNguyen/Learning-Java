-- BL-R06a: Content lưu candidate/bundle bất biến; không tạo publication phase hoặc outbox.
CREATE TABLE content.publication_canonical_head (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  revision bigint NOT NULL CHECK(revision > 0),
  bundle_id text NOT NULL,
  bundle_digest text NOT NULL CHECK(bundle_digest ~ '^[0-9a-f]{64}$'),
  lock_token integer NOT NULL DEFAULT 0 CHECK(lock_token = 0)
);

CREATE TABLE content.publication_bundle (
  bundle_id text PRIMARY KEY CHECK(bundle_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
  target_revision bigint NOT NULL CHECK(target_revision > 0),
  bundle_bytes bytea NOT NULL CHECK(octet_length(bundle_bytes) BETWEEN 1 AND 1048576),
  bundle_digest text NOT NULL CHECK(bundle_digest ~ '^[0-9a-f]{64}$'),
  manifest_bytes bytea NOT NULL CHECK(octet_length(manifest_bytes) BETWEEN 2 AND 262144),
  manifest_digest text NOT NULL CHECK(manifest_digest ~ '^[0-9a-f]{64}$'),
  validated boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(target_revision, bundle_id)
);

CREATE TABLE content.publication_candidate (
  operation_id uuid PRIMARY KEY,
  reservation_generation bigint NOT NULL CHECK(reservation_generation > 0),
  parent_revision bigint NOT NULL CHECK(parent_revision > 0),
  target_revision bigint NOT NULL CHECK(target_revision = parent_revision + 1),
  bundle_id text NOT NULL REFERENCES content.publication_bundle(bundle_id),
  candidate_digest text NOT NULL CHECK(candidate_digest ~ '^[0-9a-f]{64}$'),
  candidate_bytes bytea NOT NULL CHECK(octet_length(candidate_bytes) BETWEEN 2 AND 524288),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- Candidate history không giữ unique target/bundle: sau một abort hợp lệ, một
-- operation mới được phép thử lại P -> P+1 và có thể tái dùng exact bundle.
-- Singleton reservation mới là nguồn serialize operation đang active.
CREATE TABLE content.publication_candidate_reservation (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  generation bigint NOT NULL CHECK(generation > 0),
  operation_id uuid NULL REFERENCES content.publication_candidate(operation_id)
);
INSERT INTO content.publication_candidate_reservation(singleton, generation, operation_id)
VALUES (true, 1, NULL);

CREATE FUNCTION content.claim_publication_candidate(p_operation_id uuid, p_generation bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, content
AS $$
DECLARE
  current_generation bigint;
  current_operation uuid;
BEGIN
  SELECT generation, operation_id
    INTO current_generation, current_operation
    FROM content.publication_candidate_reservation
   WHERE singleton
   FOR UPDATE;
  IF current_generation <> p_generation THEN
    RAISE EXCEPTION 'PUBLICATION_RESERVATION_GENERATION_CONFLICT' USING ERRCODE = '23505';
  END IF;
  IF current_operation IS NULL THEN
    UPDATE content.publication_candidate_reservation
       SET operation_id = p_operation_id
     WHERE singleton;
  ELSIF current_operation <> p_operation_id THEN
    RAISE EXCEPTION 'PUBLICATION_ACTIVE_CONFLICT' USING ERRCODE = '23505';
  END IF;
END $$;

CREATE FUNCTION content.deny_publication_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Immutable publication record' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER publication_bundle_immutable
  BEFORE UPDATE OR DELETE ON content.publication_bundle
  FOR EACH ROW EXECUTE FUNCTION content.deny_publication_immutable();
CREATE TRIGGER publication_candidate_immutable
  BEFORE UPDATE OR DELETE ON content.publication_candidate
  FOR EACH ROW EXECUTE FUNCTION content.deny_publication_immutable();

-- R06a runtime chỉ prepare/read/claim. Chỉ trusted coordinator R06c tương lai
-- mới được tăng generation và clear reservation sau durable abort/commit.
REVOKE INSERT, UPDATE, DELETE ON content.publication_canonical_head FROM content_app;
GRANT UPDATE(lock_token) ON content.publication_canonical_head TO content_app;
REVOKE UPDATE, DELETE ON content.publication_bundle, content.publication_candidate FROM content_app;
REVOKE INSERT, UPDATE, DELETE ON content.publication_candidate_reservation FROM content_app;
REVOKE ALL ON FUNCTION content.claim_publication_candidate(uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION content.claim_publication_candidate(uuid, bigint) TO content_app;
