CREATE TABLE learning.auth_environment (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  realm_id uuid NOT NULL, db_generation uuid NOT NULL,
  topology_version text NOT NULL CHECK(topology_version='r1-static-v1'),
  ready boolean NOT NULL DEFAULT false,
  lock_token integer NOT NULL DEFAULT 0 CHECK(lock_token=0)
);
REVOKE INSERT,UPDATE,DELETE ON learning.auth_environment FROM learning_app;
-- SELECT FOR SHARE needs UPDATE on one column; only this inert constrained column is granted.
GRANT UPDATE(lock_token) ON learning.auth_environment TO learning_app;
CREATE TABLE learning.auth_account_gate (
  user_id uuid PRIMARY KEY, realm_id uuid NOT NULL,
  min_credential_epoch bigint NOT NULL CHECK(min_credential_epoch>0)
);
-- Only deny rows exist; installing admissions never creates positive sessions.
CREATE TABLE learning.auth_session_fence (
  session_ref uuid PRIMARY KEY, context_id uuid NOT NULL UNIQUE,
  user_id uuid NOT NULL REFERENCES learning.auth_account_gate(user_id),
  realm_id uuid NOT NULL, operation_id uuid NOT NULL
);
CREATE TABLE learning.auth_command_receipt (
  operation_id uuid PRIMARY KEY,
  command_digest text NOT NULL CHECK(command_digest ~ '^[0-9a-f]{64}$'),
  payload_digest text NOT NULL CHECK(payload_digest ~ '^[0-9a-f]{64}$'),
  user_id uuid NOT NULL REFERENCES learning.auth_account_gate(user_id),
  realm_id uuid NOT NULL, db_generation uuid NOT NULL,
  scope text NOT NULL CHECK(scope IN ('SESSION','ACCOUNT')),
  applied_floor bigint NOT NULL CHECK(applied_floor>0), session_ref uuid, context_id uuid,
  CHECK((scope='SESSION' AND session_ref IS NOT NULL AND context_id IS NOT NULL)
        OR (scope='ACCOUNT' AND session_ref IS NULL AND context_id IS NULL))
);
CREATE TABLE learning.auth_admission_use (
  admission_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES learning.auth_account_gate(user_id),
  request_binding text NOT NULL CHECK(request_binding ~ '^[0-9a-f]{64}$'),
  used_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE UPDATE,DELETE ON learning.auth_session_fence,learning.auth_command_receipt,learning.auth_admission_use FROM learning_app;
REVOKE DELETE ON learning.auth_account_gate FROM learning_app;
CREATE FUNCTION learning.guard_auth_floor() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.user_id<>OLD.user_id OR NEW.realm_id<>OLD.realm_id
     OR NEW.min_credential_epoch<OLD.min_credential_epoch THEN
    RAISE EXCEPTION 'Auth floor is monotonic' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER auth_floor_monotonic BEFORE UPDATE ON learning.auth_account_gate
FOR EACH ROW EXECUTE FUNCTION learning.guard_auth_floor();
