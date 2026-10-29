CREATE TABLE identity.app_user (
  user_id uuid PRIMARY KEY,
  login_name varchar(32) NOT NULL UNIQUE,
  password_hash text,
  state text NOT NULL CHECK (state IN ('INVITED','ACTIVE','DISABLED','RECOVERY_REQUIRED')),
  operator_disabled boolean NOT NULL DEFAULT false,
  credential_epoch bigint NOT NULL DEFAULT 0 CHECK (credential_epoch >= 0),
  auth_transition_pending boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK (login_name ~ '^[a-z0-9][a-z0-9._-]{2,31}$'),
  CHECK ((state = 'INVITED' AND password_hash IS NULL) OR state <> 'INVITED'),
  CHECK (state <> 'ACTIVE' OR (password_hash IS NOT NULL AND NOT operator_disabled)),
  CHECK (state <> 'DISABLED' OR operator_disabled),
  CHECK (state <> 'RECOVERY_REQUIRED' OR password_hash IS NULL),
  CHECK (state <> 'INVITED' OR NOT operator_disabled)
);

CREATE TABLE identity.credential_token (
  token_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES identity.app_user(user_id),
  purpose text NOT NULL CHECK (purpose IN ('ACTIVATE','RESET')),
  token_digest bytea NOT NULL UNIQUE CHECK (octet_length(token_digest) = 32),
  issued_epoch bigint NOT NULL CHECK (issued_epoch >= 0),
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  invalidated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK (expires_at > created_at),
  CHECK (consumed_at IS NULL OR invalidated_at IS NULL)
);
CREATE INDEX credential_token_user_purpose_open_idx
  ON identity.credential_token(user_id,purpose) WHERE consumed_at IS NULL AND invalidated_at IS NULL;

-- Credential changes commit this intent atomically. R04 owns delivery, ACK and
-- clearing auth_transition_pending; R03 never declares distributed completion.
CREATE TABLE identity.credential_transition (
  operation_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES identity.app_user(user_id),
  credential_epoch bigint NOT NULL CHECK (credential_epoch > 0),
  action text NOT NULL CHECK (action IN ('RESET','DISABLED','ACTIVE','RECOVERY_REQUIRED')),
  state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','COMPLETED')),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(user_id,credential_epoch)
);

-- Singleton row serializes new throttle key admission, not KDF work. A database
-- trigger enforces the hard cap even when a runtime caller forgets admission.
CREATE TABLE identity.auth_throttle_capacity (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  max_keys integer NOT NULL CHECK (max_keys BETWEEN 1 AND 100000)
);
INSERT INTO identity.auth_throttle_capacity VALUES (true,100000);
REVOKE INSERT, UPDATE, DELETE ON identity.auth_throttle_capacity FROM identity_app;

CREATE TABLE identity.auth_throttle (
  key_digest bytea NOT NULL CHECK (octet_length(key_digest) = 32),
  bucket_kind text NOT NULL CHECK (bucket_kind IN ('ACCOUNT','IP','ACTIVATE','RESET')),
  window_started_at timestamptz NOT NULL,
  failed_count integer NOT NULL CHECK (failed_count BETWEEN 0 AND 30),
  in_flight integer NOT NULL DEFAULT 0 CHECK (in_flight BETWEEN 0 AND 30),
  blocked_until timestamptz,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (bucket_kind,key_digest)
);
CREATE INDEX auth_throttle_expiry_idx ON identity.auth_throttle(expires_at);

CREATE FUNCTION identity.check_throttle_capacity() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
DECLARE allowed integer;
BEGIN
  PERFORM pg_advisory_xact_lock(7303,1);
  SELECT max_keys INTO allowed FROM identity.auth_throttle_capacity WHERE singleton;
  IF NOT EXISTS (SELECT 1 FROM identity.auth_throttle WHERE bucket_kind=NEW.bucket_kind AND key_digest=NEW.key_digest)
     AND (SELECT count(*) FROM identity.auth_throttle) >= allowed THEN
    RAISE EXCEPTION 'Throttle capacity exhausted' USING ERRCODE='P0001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER throttle_capacity_guard BEFORE INSERT ON identity.auth_throttle
FOR EACH ROW EXECUTE FUNCTION identity.check_throttle_capacity();

CREATE TABLE identity.security_audit (
  audit_id uuid PRIMARY KEY,
  event_type text NOT NULL,
  target_user_id uuid,
  result text NOT NULL CHECK (result IN ('SUCCEEDED','DENIED','FAILED')),
  correlation_id uuid NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  detail_code text NOT NULL,
  CHECK (length(event_type) BETWEEN 1 AND 64),
  CHECK (length(detail_code) BETWEEN 1 AND 64)
);
CREATE INDEX security_audit_time_idx ON identity.security_audit(occurred_at);
REVOKE UPDATE, DELETE ON identity.security_audit FROM identity_app;

REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA identity FROM identity_app;
