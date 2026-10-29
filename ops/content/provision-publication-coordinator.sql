-- BL-R06c owned-lab provisioning as content_migrator after V5 and auth realm.
-- Required session settings mirror the fixed three-service lab topology.
DO $$
DECLARE
  realm uuid;
  content_generation uuid;
  learning_generation uuid;
  capability bytea;
  canonical_revision bigint;
  saved content.publication_environment%ROWTYPE;
BEGIN
  IF current_database() <> 'content_db'
      OR current_user <> 'content_migrator'
      OR current_setting('backend_lab.run_id')
        <> current_setting('backend_lab.provision_run_id') THEN
    RAISE EXCEPTION 'Owned Content lab required';
  END IF;
  realm := current_setting('backend_lab.provision_realm')::uuid;
  content_generation := current_setting('backend_lab.provision_content_generation')::uuid;
  learning_generation := current_setting('backend_lab.provision_learning_generation')::uuid;
  capability := decode(current_setting('backend_lab.provision_publication_capability'), 'hex');
  IF octet_length(capability) < 32 OR octet_length(capability) > 128 THEN
    RAISE EXCEPTION 'Publication coordinator capability invalid';
  END IF;
  PERFORM pg_advisory_xact_lock(7402, 6);

  IF NOT EXISTS (
    SELECT 1 FROM content.auth_environment
     WHERE singleton
       AND realm_id = realm
       AND db_generation = content_generation
       AND ready
  ) THEN
    RAISE EXCEPTION 'Current Content auth realm must be provisioned first';
  END IF;
  SELECT revision INTO canonical_revision
    FROM content.publication_canonical_head
   WHERE singleton
   FOR UPDATE;
  IF canonical_revision IS NULL THEN
    RAISE EXCEPTION 'Canonical publication head must exist first';
  END IF;

  SELECT * INTO saved
    FROM content.publication_environment
   WHERE singleton
   FOR UPDATE;
  IF FOUND THEN
    IF saved.realm_id <> realm
        OR saved.producer_generation <> content_generation
        OR saved.participant_generation <> learning_generation
        OR saved.coordinator_capability_sha256 <> sha256(capability)
        OR saved.learning_ready_revision <> canonical_revision THEN
      RAISE EXCEPTION 'Existing publication environment cannot be reprovisioned';
    END IF;
  ELSE
    IF EXISTS (SELECT 1 FROM content.publication_operation)
        OR EXISTS (SELECT 1 FROM content.publication_phase_outbox)
        OR EXISTS (SELECT 1 FROM content.publication_phase_receipt) THEN
      RAISE EXCEPTION 'Cannot provision over retained coordinator state';
    END IF;
    INSERT INTO content.publication_environment(
      singleton,
      realm_id,
      producer_generation,
      participant_generation,
      coordinator_capability_sha256,
      learning_ready_revision
    ) VALUES (
      true,
      realm,
      content_generation,
      learning_generation,
      sha256(capability),
      canonical_revision
    );
  END IF;
END $$;
