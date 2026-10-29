-- R02c own durable delivery foundation; app never provisions/rotates environments.
CREATE TABLE content.delivery_environment (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  realm_id uuid NOT NULL, db_generation uuid NOT NULL,
  identity_generation uuid NOT NULL, content_generation uuid NOT NULL, learning_generation uuid NOT NULL,
  ready boolean NOT NULL DEFAULT false, maintenance boolean NOT NULL DEFAULT false,
  lock_token integer NOT NULL DEFAULT 0 CHECK(lock_token=0),
  CHECK(content_generation=db_generation)
);
REVOKE INSERT,UPDATE,DELETE ON content.delivery_environment FROM content_app;
GRANT UPDATE(lock_token) ON content.delivery_environment TO content_app;
CREATE TABLE content.delivery_producer_stream (
  consumer text NOT NULL CHECK(consumer IN ('identity','content','learning')),
  stream_key text NOT NULL CHECK(length(stream_key) BETWEEN 1 AND 128),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL,
  next_sequence bigint NOT NULL DEFAULT 1 CHECK(next_sequence>0),
  PRIMARY KEY(consumer,stream_key,realm_id,producer_generation)
);
CREATE TABLE content.delivery_outbox (
  message_id uuid PRIMARY KEY, operation_id uuid NOT NULL, correlation_id uuid NOT NULL,
  consumer text NOT NULL CHECK(consumer IN ('identity','content','learning')),
  stream_key text NOT NULL, delivery_sequence bigint NOT NULL CHECK(delivery_sequence>0),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL, participant_generation uuid NOT NULL,
  message_type text NOT NULL, phase text NOT NULL,
  envelope_bytes bytea NOT NULL CHECK(octet_length(envelope_bytes) BETWEEN 1 AND 8192),
  envelope_digest text NOT NULL CHECK(envelope_digest ~ '^[0-9a-f]{64}$'),
  payload_digest text NOT NULL CHECK(payload_digest ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL,
  delivery_status text NOT NULL DEFAULT 'PENDING' CHECK(delivery_status IN ('PENDING','DELIVERED','PAUSED','ABORT_PENDING','REJECTED_NO_EFFECT')),
  attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
  next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  lease_token uuid, lease_until timestamptz, ack_bytes bytea,
  last_error text CHECK(last_error IS NULL OR last_error ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  UNIQUE(consumer,stream_key,realm_id,producer_generation,delivery_sequence),
  UNIQUE(operation_id,consumer,message_type,phase),
  CHECK((lease_token IS NULL)=(lease_until IS NULL)),
  CHECK((delivery_status IN ('DELIVERED','REJECTED_NO_EFFECT'))=(ack_bytes IS NOT NULL))
);
CREATE INDEX delivery_due ON content.delivery_outbox(next_attempt_at) WHERE delivery_status='PENDING';
CREATE TABLE content.delivery_receiver_stream (
  producer text NOT NULL CHECK(producer IN ('identity','content','learning')),
  stream_key text NOT NULL CHECK(length(stream_key) BETWEEN 1 AND 128),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL, participant_generation uuid NOT NULL,
  last_sequence bigint NOT NULL DEFAULT 0 CHECK(last_sequence>=0),
  receipt_head text NOT NULL DEFAULT repeat('0',64) CHECK(receipt_head ~ '^[0-9a-f]{64}$'),
  PRIMARY KEY(producer,stream_key,realm_id,producer_generation,participant_generation)
);
CREATE TABLE content.delivery_inbox (
  message_id uuid PRIMARY KEY, operation_id uuid NOT NULL,
  producer text NOT NULL CHECK(producer IN ('identity','content','learning')), stream_key text NOT NULL,
  delivery_sequence bigint NOT NULL CHECK(delivery_sequence>0),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL, participant_generation uuid NOT NULL,
  message_type text NOT NULL, phase text NOT NULL,
  envelope_digest text NOT NULL CHECK(envelope_digest ~ '^[0-9a-f]{64}$'),
  payload_digest text NOT NULL CHECK(payload_digest ~ '^[0-9a-f]{64}$'),
  disposition text NOT NULL CHECK(disposition IN ('APPLIED','REJECTED_NO_EFFECT')),
  receipt_head text NOT NULL CHECK(receipt_head ~ '^[0-9a-f]{64}$'),
  ack_bytes bytea NOT NULL CHECK(octet_length(ack_bytes) BETWEEN 1 AND 8192),
  committed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(producer,stream_key,realm_id,producer_generation,participant_generation,delivery_sequence)
  ,UNIQUE(operation_id,producer,message_type,phase,realm_id,producer_generation,participant_generation)
);
CREATE TABLE content.delivery_quarantine (
  message_id uuid PRIMARY KEY, operation_id uuid NOT NULL,
  producer text NOT NULL CHECK(producer IN ('identity','content','learning')), stream_key text NOT NULL,
  delivery_sequence bigint NOT NULL CHECK(delivery_sequence>0),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL, participant_generation uuid NOT NULL,
  envelope_digest text NOT NULL CHECK(envelope_digest ~ '^[0-9a-f]{64}$'),
  payload_digest text NOT NULL CHECK(payload_digest ~ '^[0-9a-f]{64}$'),
  reason text NOT NULL CHECK(reason ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  first_seen_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
-- Recovery is an immutable, versioned audit, not a reset or edited old message.
CREATE TABLE content.delivery_recovery_receipt (
  recovery_id uuid PRIMARY KEY, message_id uuid NOT NULL REFERENCES content.delivery_quarantine(message_id),
  realm_id uuid NOT NULL, participant_generation uuid NOT NULL, recovery_generation uuid NOT NULL,
  covered_prefix_digest text NOT NULL CHECK(covered_prefix_digest ~ '^[0-9a-f]{64}$'),
  offending_digest text NOT NULL CHECK(offending_digest ~ '^[0-9a-f]{64}$'),
  producer_abort_digest text NOT NULL CHECK(producer_abort_digest ~ '^[0-9a-f]{64}$'),
  audit_reason text NOT NULL CHECK(length(audit_reason) BETWEEN 1 AND 256),
  recovered_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  CHECK(recovery_generation<>participant_generation)
);
-- Maintenance coordinator version is registered by the own operator, not caller metadata.
CREATE TABLE content.delivery_recovery_authorization (
  recovery_id uuid PRIMARY KEY, recovery_generation uuid UNIQUE NOT NULL,
  message_id uuid UNIQUE NOT NULL, operation_id uuid NOT NULL,
  producer text NOT NULL CHECK(producer IN ('identity','content','learning')),
  consumer text NOT NULL CHECK(consumer IN ('identity','content','learning')),
  realm_id uuid NOT NULL, producer_generation uuid NOT NULL, participant_generation uuid NOT NULL,
  offending_digest text NOT NULL CHECK(offending_digest ~ '^[0-9a-f]{64}$'),
  covered_sequence bigint NOT NULL CHECK(covered_sequence>=0),
  covered_prefix_digest text NOT NULL CHECK(covered_prefix_digest ~ '^[0-9a-f]{64}$'),
  audit_reason text NOT NULL CHECK(audit_reason ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  state text NOT NULL DEFAULT 'AUTHORIZED' CHECK(state IN ('AUTHORIZED','CONSUMED')),
  CHECK(recovery_generation<>producer_generation AND recovery_generation<>participant_generation)
);
CREATE TABLE content.delivery_producer_abort (
  recovery_id uuid PRIMARY KEY REFERENCES content.delivery_recovery_authorization(recovery_id),
  message_id uuid UNIQUE NOT NULL REFERENCES content.delivery_outbox(message_id),
  proof_bytes bytea NOT NULL CHECK(octet_length(proof_bytes) BETWEEN 1 AND 8192),
  proof_digest text NOT NULL CHECK(proof_digest ~ '^[0-9a-f]{64}$'),
  committed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE content.delivery_recovery_receipt ADD COLUMN covered_sequence bigint NOT NULL CHECK(covered_sequence>=0);
ALTER TABLE content.delivery_recovery_receipt ADD COLUMN producer_abort_bytes bytea NOT NULL CHECK(octet_length(producer_abort_bytes) BETWEEN 1 AND 8192);
ALTER TABLE content.delivery_recovery_receipt ADD CONSTRAINT delivery_recovery_one_message UNIQUE(message_id);
REVOKE INSERT,UPDATE,DELETE ON content.delivery_recovery_authorization,content.delivery_producer_abort FROM content_app;
CREATE FUNCTION content.guard_delivery_recovery_authorization() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state')
     OR OLD.state='CONSUMED' OR NEW.state<>'CONSUMED' THEN
    RAISE EXCEPTION 'Immutable recovery authorization' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER delivery_recovery_authorization_immutable BEFORE UPDATE ON content.delivery_recovery_authorization
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_recovery_authorization();
CREATE FUNCTION content.deny_delivery_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Immutable delivery recovery provenance' USING ERRCODE='23514';
END $$;
CREATE TRIGGER delivery_producer_abort_immutable BEFORE UPDATE OR DELETE ON content.delivery_producer_abort
  FOR EACH ROW EXECUTE FUNCTION content.deny_delivery_audit_mutation();
CREATE TRIGGER delivery_recovery_receipt_immutable BEFORE UPDATE OR DELETE ON content.delivery_recovery_receipt
  FOR EACH ROW EXECUTE FUNCTION content.deny_delivery_audit_mutation();
CREATE TRIGGER delivery_recovery_authorization_retained BEFORE DELETE ON content.delivery_recovery_authorization
  FOR EACH ROW EXECUTE FUNCTION content.deny_delivery_audit_mutation();
-- Monotonic INT02 lane has no publication/lifecycle stream cursor or FK.
CREATE TABLE content.auth_delivery_outbox (
  operation_id uuid NOT NULL, consumer text NOT NULL CHECK(consumer IN ('content','learning')),
  realm_id uuid NOT NULL, participant_generation uuid NOT NULL,
  command_bytes bytea NOT NULL CHECK(octet_length(command_bytes) BETWEEN 1 AND 8192),
  command_digest text NOT NULL CHECK(command_digest ~ '^[0-9a-f]{64}$'),
  envelope_digest text NOT NULL CHECK(envelope_digest ~ '^[0-9a-f]{64}$'),
  delivery_status text NOT NULL DEFAULT 'PENDING' CHECK(delivery_status IN ('PENDING','DELIVERED','PAUSED')),
  attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 8),
  next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  lease_token uuid, lease_until timestamptz, ack_bytes bytea,
  last_error text CHECK(last_error IS NULL OR last_error ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  PRIMARY KEY(operation_id,consumer),
  CHECK((lease_token IS NULL)=(lease_until IS NULL)),
  CHECK((delivery_status='DELIVERED')=(ack_bytes IS NOT NULL))
);
REVOKE DELETE ON content.delivery_producer_stream,content.delivery_receiver_stream,content.delivery_outbox,content.auth_delivery_outbox FROM content_app;
REVOKE UPDATE,DELETE ON content.delivery_inbox,content.delivery_quarantine,content.delivery_recovery_receipt FROM content_app;
REVOKE INSERT ON content.delivery_recovery_receipt FROM content_app;
CREATE FUNCTION content.guard_delivery_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (to_jsonb(NEW)-ARRAY['delivery_status','attempts','next_attempt_at','lease_token','lease_until','ack_bytes','last_error'])
     IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['delivery_status','attempts','next_attempt_at','lease_token','lease_until','ack_bytes','last_error'])
     OR NEW.attempts<OLD.attempts
     OR (OLD.delivery_status IN ('DELIVERED','REJECTED_NO_EFFECT') AND NEW IS DISTINCT FROM OLD) THEN
    RAISE EXCEPTION 'Immutable delivery or terminal receipt' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER delivery_outbox_immutable BEFORE UPDATE ON content.delivery_outbox
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_immutable();
CREATE TRIGGER auth_delivery_immutable BEFORE UPDATE ON content.auth_delivery_outbox
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_immutable();
CREATE FUNCTION content.guard_delivery_sequence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME='delivery_producer_stream' THEN
    IF (to_jsonb(NEW)-'next_sequence') IS DISTINCT FROM (to_jsonb(OLD)-'next_sequence')
       OR NEW.next_sequence<>OLD.next_sequence+1 THEN
      RAISE EXCEPTION 'Producer sequence cannot skip or rewind' USING ERRCODE='23514';
    END IF;
  ELSE
    IF (to_jsonb(NEW)-ARRAY['last_sequence','receipt_head']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['last_sequence','receipt_head'])
       OR NEW.last_sequence<>OLD.last_sequence+1 THEN
      RAISE EXCEPTION 'Receiver sequence cannot skip or rewind' USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER delivery_producer_monotonic BEFORE UPDATE ON content.delivery_producer_stream
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_sequence();
CREATE TRIGGER delivery_receiver_monotonic BEFORE UPDATE ON content.delivery_receiver_stream
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_sequence();
CREATE FUNCTION content.guard_delivery_initial_sequence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (TG_TABLE_NAME='delivery_producer_stream' AND NEW.next_sequence<>1) THEN
    RAISE EXCEPTION 'Producer must start at one' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER delivery_producer_initial BEFORE INSERT ON content.delivery_producer_stream
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_initial_sequence();
CREATE FUNCTION content.guard_delivery_initial_cursor() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.last_sequence<>0 OR NEW.receipt_head<>repeat('0',64) THEN
    RAISE EXCEPTION 'Receiver must start with empty prefix' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER delivery_receiver_initial BEFORE INSERT ON content.delivery_receiver_stream
  FOR EACH ROW EXECUTE FUNCTION content.guard_delivery_initial_cursor();
