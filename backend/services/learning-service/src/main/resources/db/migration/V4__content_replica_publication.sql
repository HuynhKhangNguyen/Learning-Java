-- R06b: Learning owns verified replicas and its participant state; Content owns canonical head.
CREATE TABLE learning.publication_environment (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), realm_id uuid NOT NULL,
  db_generation uuid NOT NULL, content_generation uuid NOT NULL, ready boolean NOT NULL DEFAULT false
);
REVOKE INSERT,UPDATE,DELETE ON learning.publication_environment FROM learning_app;

CREATE TABLE learning.content_replica (
  bundle_id text PRIMARY KEY CHECK(bundle_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'),
  target_revision bigint UNIQUE NOT NULL CHECK(target_revision>0),
  bundle_sha256 text UNIQUE NOT NULL CHECK(bundle_sha256 ~ '^[0-9a-f]{64}$'),
  manifest_sha256 text NOT NULL CHECK(manifest_sha256 ~ '^[0-9a-f]{64}$'),
  bundle_bytes bytea NOT NULL CHECK(octet_length(bundle_bytes) BETWEEN 1 AND 1048576),
  manifest_bytes bytea NOT NULL CHECK(octet_length(manifest_bytes) BETWEEN 2 AND 262144),
  verified_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE learning.publication_head (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), applied_revision bigint NOT NULL CHECK(applied_revision>0),
  active_bundle_id text REFERENCES learning.content_replica(bundle_id), active_manifest_sha256 text,
  fence_operation uuid UNIQUE
);
CREATE TABLE learning.publication_operation (
  operation_id uuid PRIMARY KEY, parent_revision bigint NOT NULL, target_revision bigint NOT NULL,
  bundle_id text NOT NULL REFERENCES learning.content_replica(bundle_id), bundle_sha256 text NOT NULL,
  manifest_sha256 text NOT NULL, realm_id uuid NOT NULL, producer_generation uuid NOT NULL,
  participant_generation uuid NOT NULL, state text NOT NULL CHECK(state IN ('STAGED','FENCED','RELEASED','ABORTED')),
  CHECK(target_revision=parent_revision+1)
);
CREATE TABLE learning.publication_receipt (
  operation_id uuid NOT NULL REFERENCES learning.publication_operation(operation_id),
  phase text NOT NULL CHECK(phase IN ('STAGE','FENCE','RELEASE','ABORT')),
  delivery_sequence bigint NOT NULL CHECK(delivery_sequence>0), command_sha256 text NOT NULL CHECK(command_sha256 ~ '^[0-9a-f]{64}$'),
  receipt_bytes bytea NOT NULL CHECK(octet_length(receipt_bytes) BETWEEN 2 AND 8192), committed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY(operation_id,phase), UNIQUE(delivery_sequence)
);
CREATE TABLE learning.publication_delivery_cursor (
  stream_key text PRIMARY KEY CHECK(stream_key='content-global'),
  last_sequence bigint NOT NULL DEFAULT 0 CHECK(last_sequence>=0)
);
CREATE FUNCTION learning.guard_publication_delivery_cursor() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  -- Cursor chỉ tiến đúng một bước sau khi receipt cùng sequence đã tồn tại trong transaction.
  IF NEW.stream_key IS DISTINCT FROM OLD.stream_key OR NEW.last_sequence<>OLD.last_sequence+1
     OR NOT EXISTS(SELECT 1 FROM learning.publication_receipt WHERE delivery_sequence=NEW.last_sequence) THEN
    RAISE EXCEPTION 'Publication delivery cursor cannot skip or rewind' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER publication_delivery_cursor_monotonic BEFORE UPDATE ON learning.publication_delivery_cursor
  FOR EACH ROW EXECUTE FUNCTION learning.guard_publication_delivery_cursor();
GRANT SELECT ON learning.publication_environment,learning.content_replica,learning.publication_head,learning.publication_operation,learning.publication_receipt,learning.publication_delivery_cursor TO learning_app;
GRANT INSERT ON learning.content_replica,learning.publication_operation,learning.publication_receipt TO learning_app;
GRANT UPDATE ON learning.publication_head TO learning_app;
-- Operation context đã bind vào receipt là bất biến; app chỉ được chuyển state theo state machine trong store.
REVOKE UPDATE ON learning.publication_operation FROM learning_app;
GRANT UPDATE (state) ON learning.publication_operation TO learning_app;
GRANT UPDATE (last_sequence) ON learning.publication_delivery_cursor TO learning_app;
REVOKE UPDATE,DELETE ON learning.content_replica,learning.publication_receipt FROM learning_app;
REVOKE INSERT,DELETE ON learning.publication_delivery_cursor FROM learning_app;
REVOKE DELETE ON learning.publication_operation,learning.publication_head FROM learning_app;
CREATE FUNCTION learning.deny_publication_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  RAISE EXCEPTION 'Immutable publication data' USING ERRCODE='23514';
END $$;
CREATE TRIGGER content_replica_immutable BEFORE UPDATE OR DELETE ON learning.content_replica FOR EACH ROW EXECUTE FUNCTION learning.deny_publication_immutable();
CREATE TRIGGER publication_receipt_immutable BEFORE UPDATE OR DELETE ON learning.publication_receipt FOR EACH ROW EXECUTE FUNCTION learning.deny_publication_immutable();
