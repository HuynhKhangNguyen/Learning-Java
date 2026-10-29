-- R04a: local canonical deny and durable intents only. No ACK/completion writer.
CREATE TABLE identity.auth_environment (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  realm_id uuid NOT NULL,
  topology_version text NOT NULL CHECK (topology_version = 'r1-static-v1')
);
REVOKE INSERT, UPDATE, DELETE ON identity.auth_environment FROM identity_app;

CREATE TABLE identity.auth_preauth_capacity (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  max_flows integer NOT NULL CHECK (max_flows BETWEEN 1 AND 100000)
);
INSERT INTO identity.auth_preauth_capacity VALUES (true,100000);
REVOKE INSERT,UPDATE,DELETE ON identity.auth_preauth_capacity FROM identity_app;

CREATE TABLE identity.auth_preauth (
  cookie_digest bytea PRIMARY KEY CHECK (octet_length(cookie_digest)=32),
  context_id uuid NOT NULL UNIQUE,
  realm_id uuid NOT NULL,
  csrf_token varchar(43),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  expires_at timestamptz NOT NULL DEFAULT (clock_timestamp()+interval '15 minutes'),
  consumed_at timestamptz,
  CHECK (expires_at>created_at),
  CHECK ((consumed_at IS NULL AND csrf_token IS NOT NULL AND csrf_token ~ '^[A-Za-z0-9_-]{43}$') OR
         (consumed_at IS NOT NULL AND csrf_token IS NULL))
);
CREATE INDEX auth_preauth_expiry_idx ON identity.auth_preauth(expires_at);

CREATE FUNCTION identity.check_preauth_capacity() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
DECLARE allowed integer;
BEGIN
  PERFORM pg_advisory_xact_lock(7404,1);
  SELECT max_flows INTO allowed FROM identity.auth_preauth_capacity WHERE singleton;
  IF (SELECT count(*) FROM identity.auth_preauth)>=allowed THEN
    RAISE EXCEPTION 'Preauth capacity exhausted' USING ERRCODE='P0001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER preauth_capacity_guard BEFORE INSERT ON identity.auth_preauth
FOR EACH ROW EXECUTE FUNCTION identity.check_preauth_capacity();

CREATE TABLE identity.auth_session (
  session_ref uuid PRIMARY KEY,
  cookie_digest bytea NOT NULL UNIQUE CHECK (octet_length(cookie_digest)=32),
  framework_id varchar(64) NOT NULL UNIQUE CHECK (framework_id ~ '^[0-9a-f]{64}$'),
  context_id uuid NOT NULL UNIQUE,
  user_id uuid NOT NULL REFERENCES identity.app_user(user_id),
  credential_epoch bigint NOT NULL CHECK (credential_epoch>0),
  realm_id uuid NOT NULL,
  state text NOT NULL CHECK (state IN ('ACTIVE','REVOKING','EXPIRED','REVOKED')),
  csrf_token varchar(43),
  issued_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  last_interactive_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK (last_interactive_at>=issued_at),
  CHECK ((state='ACTIVE' AND csrf_token IS NOT NULL AND csrf_token ~ '^[A-Za-z0-9_-]{43}$') OR
         (state<>'ACTIVE' AND csrf_token IS NULL))
);
CREATE INDEX auth_session_user_idx ON identity.auth_session(user_id);

CREATE TABLE identity.auth_operation (
  operation_id uuid PRIMARY KEY,
  idempotency_key uuid NOT NULL,
  initiating_cookie_digest bytea NOT NULL CHECK (octet_length(initiating_cookie_digest)=32),
  initiating_context_id uuid NOT NULL,
  initiating_csrf_digest bytea NOT NULL CHECK (octet_length(initiating_csrf_digest)=32),
  target_user_id uuid NOT NULL REFERENCES identity.app_user(user_id),
  target_session_ref uuid REFERENCES identity.auth_session(session_ref),
  credential_epoch bigint NOT NULL CHECK (credential_epoch>0),
  realm_id uuid NOT NULL,
  scope text NOT NULL CHECK (scope IN ('SESSION','ACCOUNT')),
  action text NOT NULL CHECK (action IN ('LOGOUT','EXPIRY','RESET','LOGOUT_ALL','PASSWORD_CHANGE')),
  command_digest bytea NOT NULL CHECK (octet_length(command_digest)=32),
  topology_version text NOT NULL CHECK (topology_version='r1-static-v1'),
  required_participants text[] NOT NULL CHECK (required_participants=ARRAY['content','learning']::text[]),
  state text NOT NULL DEFAULT 'PENDING_FENCES' CHECK (state='PENDING_FENCES'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(initiating_cookie_digest,initiating_context_id,idempotency_key),
  CHECK ((scope='SESSION' AND target_session_ref IS NOT NULL AND action IN ('LOGOUT','EXPIRY')) OR
         (scope='ACCOUNT' AND target_session_ref IS NULL AND action IN ('RESET','LOGOUT_ALL','PASSWORD_CHANGE')))
);
CREATE TABLE identity.auth_fence_intent (
  operation_id uuid NOT NULL REFERENCES identity.auth_operation(operation_id),
  participant text NOT NULL CHECK (participant IN ('content','learning')),
  command_digest bytea NOT NULL CHECK (octet_length(command_digest)=32),
  state text NOT NULL DEFAULT 'PENDING' CHECK (state='PENDING'),
  PRIMARY KEY(operation_id,participant)
);

-- Framework session id is the digest of the browser handle, never the handle.
-- Its attributes are empty: no serialized SecurityContext/principal can grant access.
CREATE TABLE identity.spring_session (
  primary_id char(36) PRIMARY KEY,
  session_id varchar(64) NOT NULL UNIQUE,
  creation_time bigint NOT NULL,
  last_access_time bigint NOT NULL,
  max_inactive_interval integer NOT NULL,
  expiry_time bigint NOT NULL,
  principal_name varchar(100)
);
CREATE INDEX spring_session_expiry_idx ON identity.spring_session(expiry_time);
CREATE TABLE identity.spring_session_attributes (
  session_primary_id char(36) NOT NULL REFERENCES identity.spring_session(primary_id) ON DELETE CASCADE,
  attribute_name varchar(200) NOT NULL,
  attribute_bytes bytea NOT NULL,
  PRIMARY KEY(session_primary_id,attribute_name)
);

CREATE FUNCTION identity.guard_session_transition() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  IF (NEW.session_ref,NEW.cookie_digest,NEW.framework_id,NEW.context_id,NEW.user_id,NEW.credential_epoch,NEW.realm_id,NEW.issued_at)
       IS DISTINCT FROM
     (OLD.session_ref,OLD.cookie_digest,OLD.framework_id,OLD.context_id,OLD.user_id,OLD.credential_epoch,OLD.realm_id,OLD.issued_at)
     OR NEW.last_interactive_at<OLD.last_interactive_at
     OR (OLD.state<>'ACTIVE' AND NEW.state='ACTIVE')
     OR (OLD.state='REVOKED' AND NEW.state<>'REVOKED')
     OR (OLD.state='EXPIRED' AND NEW.state NOT IN ('EXPIRED','REVOKED'))
     OR (OLD.state='REVOKING' AND NEW.state NOT IN ('REVOKING','REVOKED')) THEN
    RAISE EXCEPTION 'Session transition denied' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER session_transition_guard BEFORE UPDATE ON identity.auth_session
FOR EACH ROW EXECUTE FUNCTION identity.guard_session_transition();

CREATE FUNCTION identity.guard_preauth_transition() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  IF (NEW.cookie_digest,NEW.context_id,NEW.realm_id,NEW.created_at,NEW.expires_at)
       IS DISTINCT FROM (OLD.cookie_digest,OLD.context_id,OLD.realm_id,OLD.created_at,OLD.expires_at)
     OR (OLD.consumed_at IS NOT NULL AND NEW IS DISTINCT FROM OLD) THEN
    RAISE EXCEPTION 'Preauth transition denied' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER preauth_transition_guard BEFORE UPDATE ON identity.auth_preauth
FOR EACH ROW EXECUTE FUNCTION identity.guard_preauth_transition();

-- Terminal guards/bindings must survive a late flush or retry. Retention is an
-- operator protocol, not arbitrary runtime DELETE/TRUNCATE or metadata rewrite.
REVOKE DELETE ON identity.auth_preauth,identity.auth_session FROM identity_app;
REVOKE UPDATE,DELETE ON identity.auth_operation,identity.auth_fence_intent FROM identity_app;
REVOKE TRUNCATE,REFERENCES,TRIGGER ON ALL TABLES IN SCHEMA identity FROM identity_app;
