-- BL-R07 chỉ nhận release input. Chạy bằng content_migrator sau khi set:
-- backend_lab.browser_operation_id, browser_revision, browser_bundle_id,
-- browser_bundle_digest, browser_snapshot_sha256 and browser_snapshot_hex.
DO $$
DECLARE
  requested_operation uuid := current_setting('backend_lab.browser_operation_id')::uuid;
  requested_revision bigint := current_setting('backend_lab.browser_revision')::bigint;
  requested_bundle text := current_setting('backend_lab.browser_bundle_id');
  requested_bundle_digest text := current_setting('backend_lab.browser_bundle_digest');
  requested_snapshot_digest text := current_setting('backend_lab.browser_snapshot_sha256');
  requested_bytes bytea := decode(current_setting('backend_lab.browser_snapshot_hex'), 'hex');
  existing content.browser_snapshot%ROWTYPE;
BEGIN
  IF current_database() <> 'content_db' OR current_user <> 'content_migrator' THEN
    RAISE EXCEPTION 'Owned Content migration role required' USING ERRCODE = '42501';
  END IF;
  PERFORM pg_advisory_xact_lock(7402, 7);
  IF requested_revision < 1
      OR requested_bundle !~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
      OR requested_bundle_digest !~ '^[0-9a-f]{64}$'
      OR requested_snapshot_digest !~ '^[0-9a-f]{64}$'
      OR octet_length(requested_bytes) NOT BETWEEN 2 AND 1048576
      OR encode(sha256(requested_bytes), 'hex') <> requested_snapshot_digest THEN
    RAISE EXCEPTION 'Browser snapshot input invalid' USING ERRCODE = '23514';
  END IF;
  IF NOT EXISTS (
    SELECT 1
      FROM content.publication_operation operation
      JOIN content.publication_candidate candidate
        ON candidate.operation_id = operation.operation_id
      JOIN content.publication_bundle bundle
        ON bundle.bundle_id = operation.bundle_id
       AND bundle.target_revision = operation.target_revision
     WHERE operation.operation_id = requested_operation
       AND operation.target_revision = requested_revision
       AND operation.bundle_id = requested_bundle
       AND operation.bundle_sha256 = requested_bundle_digest
       AND operation.state IN ('STAGE_PENDING', 'STAGED', 'FENCE_PENDING')
       AND candidate.candidate_digest = encode(sha256(candidate.candidate_bytes), 'hex')
       AND bundle.bundle_digest = requested_bundle_digest
       AND bundle.validated
  ) THEN
    RAISE EXCEPTION 'Active publication operation binding required' USING ERRCODE = '23503';
  END IF;
  IF EXISTS (
    SELECT 1 FROM content.publication_canonical_head
     WHERE singleton AND revision >= requested_revision
  ) THEN
    RAISE EXCEPTION 'Browser snapshot must precede canonical revision' USING ERRCODE = '23514';
  END IF;
  SELECT * INTO existing
    FROM content.browser_snapshot
   WHERE operation_id = requested_operation;
  IF FOUND THEN
    IF existing.revision <> requested_revision
        OR existing.bundle_id <> requested_bundle
        OR existing.bundle_digest <> requested_bundle_digest
        OR existing.snapshot_sha256 <> requested_snapshot_digest
        OR existing.snapshot_bytes <> requested_bytes
        OR existing.schema_version <> 1 THEN
      RAISE EXCEPTION 'Browser snapshot retry conflict' USING ERRCODE = '23505';
    END IF;
    RETURN;
  END IF;
  INSERT INTO content.browser_snapshot(
    operation_id,
    revision,
    bundle_id,
    bundle_digest,
    schema_version,
    snapshot_bytes,
    snapshot_sha256
  ) VALUES (
    requested_operation,
    requested_revision,
    requested_bundle,
    requested_bundle_digest,
    1,
    requested_bytes,
    requested_snapshot_digest
  );
END $$;
