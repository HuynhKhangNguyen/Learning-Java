-- BL-R07: public browser bytes phải là component đã được review trong candidate,
-- rồi được cùng publication operation xác nhận qua STAGE/FENCE trước canonical.
ALTER TABLE content.publication_bundle
  ADD CONSTRAINT publication_bundle_browser_binding
  UNIQUE (target_revision, bundle_id, bundle_digest);

CREATE TABLE content.browser_snapshot (
  operation_id uuid PRIMARY KEY REFERENCES content.publication_candidate(operation_id),
  revision bigint NOT NULL CHECK (revision > 0),
  bundle_id text NOT NULL,
  bundle_digest text NOT NULL CHECK (bundle_digest ~ '^[0-9a-f]{64}$'),
  schema_version integer NOT NULL CHECK (schema_version = 1),
  snapshot_bytes bytea NOT NULL CHECK (octet_length(snapshot_bytes) BETWEEN 2 AND 1048576),
  snapshot_sha256 text NOT NULL CHECK (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (revision, bundle_id, bundle_digest)
    REFERENCES content.publication_bundle(target_revision, bundle_id, bundle_digest),
  UNIQUE (revision, bundle_id, bundle_digest, operation_id),
  CHECK (snapshot_sha256 = encode(sha256(snapshot_bytes), 'hex'))
);

-- Binding này được trigger FENCE tạo trong cùng transaction đổi canonical head.
-- Snapshot của operation đã ABORT vì vậy không thể trở thành public chỉ do trùng revision.
CREATE TABLE content.browser_canonical_snapshot (
  revision bigint PRIMARY KEY,
  bundle_id text NOT NULL,
  bundle_digest text NOT NULL CHECK (bundle_digest ~ '^[0-9a-f]{64}$'),
  operation_id uuid NOT NULL UNIQUE,
  bound_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (revision, bundle_id, bundle_digest, operation_id)
    REFERENCES content.browser_snapshot(revision, bundle_id, bundle_digest, operation_id)
);

CREATE FUNCTION content.validate_browser_snapshot_insert() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  candidate content.publication_candidate%ROWTYPE;
  operation content.publication_operation%ROWTYPE;
  bundle content.publication_bundle%ROWTYPE;
  current_revision bigint;
  candidate_document jsonb;
  commitment_count integer;
BEGIN
  SELECT * INTO candidate
    FROM content.publication_candidate
   WHERE operation_id = NEW.operation_id;
  SELECT * INTO operation
    FROM content.publication_operation
   WHERE operation_id = NEW.operation_id;
  SELECT * INTO bundle
    FROM content.publication_bundle
   WHERE target_revision = NEW.revision
     AND bundle_id = NEW.bundle_id
     AND bundle_digest = NEW.bundle_digest
     AND validated;
  SELECT revision INTO current_revision
    FROM content.publication_canonical_head
   WHERE singleton
   FOR SHARE;

  IF candidate.operation_id IS NULL
      OR operation.operation_id IS NULL
      OR bundle.bundle_id IS NULL
      OR current_revision IS NULL
      OR candidate.candidate_digest <> encode(sha256(candidate.candidate_bytes), 'hex')
      OR candidate.target_revision <> NEW.revision
      OR candidate.bundle_id <> NEW.bundle_id
      OR operation.target_revision <> NEW.revision
      OR operation.bundle_id <> NEW.bundle_id
      OR operation.bundle_sha256 <> NEW.bundle_digest
      OR operation.state NOT IN ('STAGE_PENDING', 'STAGED', 'FENCE_PENDING')
      OR current_revision <> candidate.parent_revision
      OR current_revision >= NEW.revision THEN
    RAISE EXCEPTION 'BROWSER_SNAPSHOT_PUBLICATION_BINDING_INVALID' USING ERRCODE = '23514';
  END IF;

  BEGIN
    candidate_document := convert_from(candidate.candidate_bytes, 'UTF8')::jsonb;
    SELECT count(*) INTO commitment_count
      FROM jsonb_array_elements(candidate_document->'components') component
     WHERE component->>'id' = 'PUBLIC-BROWSER-SNAPSHOT'
       AND component->>'type' = 'catalog'
       AND component->>'version' = '1'
       AND component->>'sha256' = NEW.snapshot_sha256;
  EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'BROWSER_SNAPSHOT_CANDIDATE_INVALID' USING ERRCODE = '23514';
  END;
  IF commitment_count <> 1 THEN
    RAISE EXCEPTION 'BROWSER_SNAPSHOT_COMMITMENT_MISMATCH' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER browser_snapshot_publication_binding
  BEFORE INSERT ON content.browser_snapshot
  FOR EACH ROW EXECUTE FUNCTION content.validate_browser_snapshot_insert();

CREATE FUNCTION content.bind_browser_canonical_snapshot() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  matched_operation uuid;
  committed_digest text;
  operation_count integer;
  commitment_count integer;
  match_count integer;
BEGIN
  IF (OLD.revision, OLD.bundle_id, OLD.bundle_digest)
      IS NOT DISTINCT FROM (NEW.revision, NEW.bundle_id, NEW.bundle_digest) THEN
    RETURN NEW;
  END IF;
  -- Canonical coordinator chỉ tăng P -> P+1. Trusted migrator có thể hạ fixture
  -- trong disposable DB test; đó không phải publication decision để bind public bytes.
  IF NEW.revision <= OLD.revision THEN
    RETURN NEW;
  END IF;

  SELECT count(*), min(operation.operation_id::text)::uuid
    INTO operation_count, matched_operation
    FROM content.publication_operation operation
   WHERE operation.target_revision = NEW.revision
     AND operation.bundle_id = NEW.bundle_id
     AND operation.bundle_sha256 = NEW.bundle_digest
     AND operation.state = 'FENCE_PENDING'
     AND EXISTS (
       SELECT 1 FROM content.publication_phase_receipt receipt
        WHERE receipt.operation_id = operation.operation_id AND receipt.phase = 'STAGE'
     )
     AND EXISTS (
       SELECT 1 FROM content.publication_phase_receipt receipt
        WHERE receipt.operation_id = operation.operation_id AND receipt.phase = 'FENCE'
     );
  IF operation_count <> 1 THEN
    RAISE EXCEPTION 'BROWSER_PUBLICATION_OPERATION_INVALID' USING ERRCODE = '23514';
  END IF;

  SELECT count(*), min(component->>'sha256')
    INTO commitment_count, committed_digest
    FROM content.publication_candidate candidate,
         jsonb_array_elements(convert_from(candidate.candidate_bytes, 'UTF8')::jsonb->'components') component
   WHERE candidate.operation_id = matched_operation
     AND component->>'id' = 'PUBLIC-BROWSER-SNAPSHOT';
  -- Publication không có browser component vẫn được canonical, nhưng API sẽ
  -- fail closed vì không có binding. Khi component tồn tại, exact projection là bắt buộc.
  IF commitment_count = 0 THEN
    RETURN NEW;
  END IF;
  IF commitment_count <> 1
      OR committed_digest !~ '^[0-9a-f]{64}$'
      OR NOT EXISTS (
        SELECT 1
          FROM content.publication_candidate candidate,
               jsonb_array_elements(
                 convert_from(candidate.candidate_bytes, 'UTF8')::jsonb->'components'
               ) component
         WHERE candidate.operation_id = matched_operation
           AND component->>'id' = 'PUBLIC-BROWSER-SNAPSHOT'
           AND component->>'type' = 'catalog'
           AND component->>'version' = '1'
      ) THEN
    RAISE EXCEPTION 'BROWSER_SNAPSHOT_COMMITMENT_INVALID' USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO match_count
    FROM content.browser_snapshot snapshot
   WHERE snapshot.operation_id = matched_operation
     AND snapshot.revision = NEW.revision
     AND snapshot.bundle_id = NEW.bundle_id
     AND snapshot.bundle_digest = NEW.bundle_digest
     AND snapshot.snapshot_sha256 = committed_digest;
  IF match_count <> 1 THEN
    RAISE EXCEPTION 'BROWSER_SNAPSHOT_CANONICAL_BINDING_REQUIRED' USING ERRCODE = '23514';
  END IF;

  INSERT INTO content.browser_canonical_snapshot(
    revision,
    bundle_id,
    bundle_digest,
    operation_id
  ) VALUES (
    NEW.revision,
    NEW.bundle_id,
    NEW.bundle_digest,
    matched_operation
  );
  RETURN NEW;
END $$;

CREATE TRIGGER browser_snapshot_canonical_binding
  BEFORE UPDATE ON content.publication_canonical_head
  FOR EACH ROW EXECUTE FUNCTION content.bind_browser_canonical_snapshot();

CREATE FUNCTION content.deny_browser_snapshot_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'BROWSER_SNAPSHOT_IMMUTABLE' USING ERRCODE = '55000';
END $$;

CREATE TRIGGER browser_snapshot_immutable
  BEFORE UPDATE OR DELETE ON content.browser_snapshot
  FOR EACH ROW EXECUTE FUNCTION content.deny_browser_snapshot_mutation();
CREATE TRIGGER browser_canonical_snapshot_immutable
  BEFORE UPDATE OR DELETE ON content.browser_canonical_snapshot
  FOR EACH ROW EXECUTE FUNCTION content.deny_browser_snapshot_mutation();

REVOKE ALL ON content.browser_snapshot, content.browser_canonical_snapshot FROM content_app;
GRANT SELECT ON content.browser_snapshot, content.browser_canonical_snapshot TO content_app;
