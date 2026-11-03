-- BL-R08: own Learning allocation. Exposure không thuộc epoch để reset không tạo clean lại.
CREATE TABLE learning.learner_guard (
  user_id uuid PRIMARY KEY,
  learning_epoch bigint NOT NULL DEFAULT 1 CHECK (learning_epoch > 0),
  lifecycle_state text NOT NULL DEFAULT 'ACTIVE'
    CHECK (lifecycle_state IN ('ACTIVE', 'PENDING', 'DELETED')),
  data_revision bigint NOT NULL DEFAULT 0 CHECK (data_revision >= 0)
);

CREATE TABLE learning.allocation_attempt (
  attempt_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES learning.learner_guard(user_id),
  learning_epoch bigint NOT NULL CHECK (learning_epoch > 0),
  draft_id uuid NOT NULL UNIQUE,
  operation_id uuid NOT NULL UNIQUE,
  exercise_id text NOT NULL,
  mode text NOT NULL CHECK (mode IN ('practice', 'assessment', 'project')),
  case_id text NOT NULL,
  case_version text NOT NULL,
  family_id text NOT NULL,
  exposure_group text NOT NULL,
  equivalence_key text NOT NULL,
  rubric_version text NOT NULL,
  grader_version text NOT NULL,
  bundle_id text NOT NULL REFERENCES learning.content_replica(bundle_id),
  bundle_sha256 text NOT NULL CHECK (bundle_sha256 ~ '^[0-9a-f]{64}$'),
  manifest_sha256 text NOT NULL CHECK (manifest_sha256 ~ '^[0-9a-f]{64}$'),
  independent_at_assignment boolean NOT NULL,
  state text NOT NULL DEFAULT 'PREPARING' CHECK (state IN ('PREPARING', 'READY', 'CLOSED')),
  assigned_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (attempt_id, user_id, learning_epoch),
  UNIQUE (operation_id, attempt_id, user_id, learning_epoch),
  UNIQUE (draft_id, attempt_id, user_id, learning_epoch)
);
-- Cùng guard scope chỉ có một assignment mở, kể cả hai process khác nhau.
CREATE UNIQUE INDEX allocation_one_open_assignment
  ON learning.allocation_attempt(user_id, learning_epoch, exposure_group)
  WHERE state IN ('PREPARING', 'READY');

CREATE TABLE learning.allocation_draft (
  draft_id uuid PRIMARY KEY,
  attempt_id uuid NOT NULL UNIQUE,
  user_id uuid NOT NULL,
  learning_epoch bigint NOT NULL,
  revision bigint NOT NULL DEFAULT 0 CHECK (revision = 0),
  answer jsonb NOT NULL DEFAULT '{}'::jsonb,
  position jsonb NOT NULL DEFAULT '{}'::jsonb,
  FOREIGN KEY (draft_id, attempt_id, user_id, learning_epoch)
    REFERENCES learning.allocation_attempt(draft_id, attempt_id, user_id, learning_epoch)
);

CREATE TABLE learning.allocation_receipt (
  user_id uuid NOT NULL,
  operation text NOT NULL DEFAULT 'ALLOCATION' CHECK (operation = 'ALLOCATION'),
  idempotency_key uuid NOT NULL,
  fingerprint text NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
  attempt_id uuid NOT NULL,
  learning_epoch bigint NOT NULL,
  PRIMARY KEY (user_id, operation, idempotency_key),
  FOREIGN KEY (attempt_id, user_id, learning_epoch)
    REFERENCES learning.allocation_attempt(attempt_id, user_id, learning_epoch)
);

CREATE TABLE learning.exposure_floor (
  user_id uuid NOT NULL REFERENCES learning.learner_guard(user_id),
  equivalence_key text NOT NULL,
  family_id text NOT NULL,
  exposure_group text NOT NULL,
  first_operation_id uuid NOT NULL,
  encountered_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (user_id, equivalence_key)
);

CREATE TABLE learning.allocation_journal (
  operation_id uuid PRIMARY KEY,
  attempt_id uuid NOT NULL UNIQUE,
  user_id uuid NOT NULL,
  learning_epoch bigint NOT NULL,
  namespace text NOT NULL,
  record_bytes bytea NOT NULL CHECK (octet_length(record_bytes) BETWEEN 2 AND 8192),
  record_sha256 text NOT NULL CHECK (record_sha256 ~ '^[0-9a-f]{64}$'),
  state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'VERIFIED')),
  receipt jsonb,
  FOREIGN KEY (operation_id, attempt_id, user_id, learning_epoch)
    REFERENCES learning.allocation_attempt(operation_id, attempt_id, user_id, learning_epoch),
  CHECK ((state = 'PENDING' AND receipt IS NULL)
    OR (state = 'VERIFIED' AND receipt IS NOT NULL
      AND receipt ?& ARRAY['recordId','namespace','recordDigest','sequence','nodeDigest','verifiedHeadDigest']
      AND receipt->>'recordId' = operation_id::text
      AND receipt->>'namespace' = namespace
      AND receipt->>'recordDigest' = record_sha256))
);

CREATE FUNCTION learning.guard_allocation_ready() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  -- READY luôn cần receipt cùng allocation; T1 tương lai vẫn phải giữ learner/publication gates.
  IF (to_jsonb(NEW) - 'state') IS DISTINCT FROM (to_jsonb(OLD) - 'state') THEN
    RAISE EXCEPTION 'Allocation pinned refs are immutable' USING ERRCODE = '23514';
  END IF;
  IF OLD.state = 'CLOSED' OR (OLD.state = 'READY' AND NEW.state = 'PREPARING') THEN
    RAISE EXCEPTION 'Allocation state cannot rewind' USING ERRCODE = '23514';
  END IF;
  IF NEW.state = 'READY' AND NOT EXISTS (
    SELECT 1 FROM learning.allocation_journal
    WHERE attempt_id = NEW.attempt_id AND operation_id = NEW.operation_id
      AND user_id = NEW.user_id AND learning_epoch = NEW.learning_epoch AND state = 'VERIFIED'
  ) THEN
    RAISE EXCEPTION 'Allocation journal receipt required' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER allocation_ready_guard BEFORE UPDATE ON learning.allocation_attempt
  FOR EACH ROW EXECUTE FUNCTION learning.guard_allocation_ready();

CREATE FUNCTION learning.guard_allocation_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.state = 'VERIFIED'
     OR (to_jsonb(NEW) - 'state' - 'receipt') IS DISTINCT FROM
        (to_jsonb(OLD) - 'state' - 'receipt')
     OR NEW.state <> 'VERIFIED' THEN
    RAISE EXCEPTION 'Allocation journal cannot rewind or change bytes' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER allocation_journal_guard BEFORE UPDATE ON learning.allocation_journal
  FOR EACH ROW EXECUTE FUNCTION learning.guard_allocation_journal();

GRANT SELECT, INSERT ON learning.learner_guard, learning.allocation_attempt,
  learning.allocation_draft, learning.allocation_receipt, learning.exposure_floor,
  learning.allocation_journal TO learning_app;
REVOKE UPDATE, DELETE ON learning.allocation_attempt, learning.allocation_draft,
  learning.allocation_receipt, learning.exposure_floor, learning.allocation_journal FROM learning_app;
GRANT UPDATE (state) ON learning.allocation_attempt TO learning_app;
GRANT UPDATE (state, receipt) ON learning.allocation_journal TO learning_app;
-- Epoch/lifecycle mutation không có public endpoint trong R08; owner sau phải có protocol riêng.
REVOKE UPDATE, DELETE ON learning.learner_guard FROM learning_app;
GRANT UPDATE (data_revision) ON learning.learner_guard TO learning_app;
