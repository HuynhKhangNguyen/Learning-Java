-- BL-R06c: durable coordinator. App chỉ gọi SECURITY DEFINER transitions;
-- không được sửa head/outbox/receipt trực tiếp để tránh bỏ qua prefix.
CREATE TABLE content.publication_environment (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  realm_id uuid NOT NULL,
  producer_generation uuid NOT NULL,
  participant_generation uuid NOT NULL,
  coordinator_capability_sha256 bytea NOT NULL CHECK (octet_length(coordinator_capability_sha256) = 32),
  learning_ready_revision bigint NOT NULL CHECK (learning_ready_revision > 0),
  last_delivery_sequence bigint NOT NULL DEFAULT 0 CHECK (last_delivery_sequence >= 0)
);

CREATE TABLE content.publication_operation (
  operation_id uuid PRIMARY KEY REFERENCES content.publication_candidate(operation_id),
  reservation_generation bigint NOT NULL,
  parent_revision bigint NOT NULL,
  target_revision bigint NOT NULL CHECK (target_revision = parent_revision + 1),
  bundle_id text NOT NULL,
  bundle_sha256 text NOT NULL CHECK (bundle_sha256 ~ '^[0-9a-f]{64}$'),
  manifest_sha256 text NOT NULL CHECK (manifest_sha256 ~ '^[0-9a-f]{64}$'),
  realm_id uuid NOT NULL,
  producer_generation uuid NOT NULL,
  participant_generation uuid NOT NULL,
  state text NOT NULL CHECK (state IN (
    'STAGE_PENDING', 'STAGED', 'FENCE_PENDING', 'FENCED',
    'RELEASE_PENDING', 'COMPLETE', 'ABORT_PENDING', 'ABORTED')),
  last_sequence bigint NOT NULL CHECK (last_sequence > 0),
  abort_requested boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  completed_at timestamptz NULL
);

CREATE UNIQUE INDEX publication_one_active_operation
  ON content.publication_operation ((true))
  WHERE state NOT IN ('COMPLETE', 'ABORTED');

CREATE TABLE content.publication_phase_outbox (
  operation_id uuid NOT NULL REFERENCES content.publication_operation(operation_id),
  phase text NOT NULL CHECK (phase IN ('STAGE', 'FENCE', 'RELEASE', 'ABORT')),
  delivery_sequence bigint NOT NULL CHECK (delivery_sequence > 0),
  command_bytes bytea NOT NULL CHECK (octet_length(command_bytes) BETWEEN 2 AND 8192),
  command_sha256 text NOT NULL CHECK (command_sha256 ~ '^[0-9a-f]{64}$'),
  delivered boolean NOT NULL DEFAULT false,
  lease_token uuid NULL,
  lease_until timestamptz NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (operation_id, phase),
  UNIQUE (delivery_sequence)
);

CREATE TABLE content.publication_phase_receipt (
  operation_id uuid NOT NULL,
  phase text NOT NULL,
  receipt_bytes bytea NOT NULL CHECK (octet_length(receipt_bytes) BETWEEN 2 AND 8192),
  receipt_sha256 text NOT NULL CHECK (receipt_sha256 ~ '^[0-9a-f]{64}$'),
  command_sha256 text NOT NULL CHECK (command_sha256 ~ '^[0-9a-f]{64}$'),
  received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (operation_id, phase),
  FOREIGN KEY (operation_id, phase)
    REFERENCES content.publication_phase_outbox(operation_id, phase)
);

REVOKE ALL ON content.publication_environment, content.publication_operation,
  content.publication_phase_outbox, content.publication_phase_receipt FROM content_app;
GRANT SELECT ON content.publication_environment, content.publication_operation,
  content.publication_phase_outbox, content.publication_phase_receipt TO content_app;

CREATE FUNCTION content.start_publication(
  p_operation_id uuid,
  p_command_bytes bytea,
  p_command_sha256 text,
  p_capability bytea
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, content
AS $$
DECLARE
  candidate content.publication_candidate%ROWTYPE;
  bundle content.publication_bundle%ROWTYPE;
  environment content.publication_environment%ROWTYPE;
  reservation content.publication_candidate_reservation%ROWTYPE;
  command jsonb;
  existing_sequence bigint;
  requested_sequence bigint;
  current_head bigint;
BEGIN
  IF p_command_sha256 !~ '^[0-9a-f]{64}$'
      OR octet_length(p_command_bytes) NOT BETWEEN 2 AND 8192 THEN
    RAISE EXCEPTION 'PUBLICATION_COMMAND_INVALID' USING ERRCODE = '23514';
  END IF;
  IF p_command_sha256 <> encode(sha256(p_command_bytes), 'hex') THEN
    RAISE EXCEPTION 'PUBLICATION_COMMAND_DIGEST_MISMATCH' USING ERRCODE = '23514';
  END IF;

  SELECT * INTO reservation
    FROM content.publication_candidate_reservation
   WHERE singleton
   FOR UPDATE;
  SELECT * INTO candidate
    FROM content.publication_candidate
   WHERE operation_id = p_operation_id;
  SELECT * INTO bundle
    FROM content.publication_bundle
   WHERE bundle_id = candidate.bundle_id AND validated;
  SELECT * INTO environment
    FROM content.publication_environment
   WHERE singleton
   FOR UPDATE;
  IF NOT COALESCE(
      octet_length(p_capability) BETWEEN 32 AND 128
      AND sha256(p_capability) = environment.coordinator_capability_sha256,
      false) THEN
    RAISE EXCEPTION 'PUBLICATION_COORDINATOR_CAPABILITY_DENIED' USING ERRCODE = '42501';
  END IF;
  SELECT revision INTO current_head
    FROM content.publication_canonical_head
   WHERE singleton
   FOR UPDATE;

  BEGIN
    command := convert_from(p_command_bytes, 'UTF8')::jsonb;
  EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'PUBLICATION_COMMAND_JSON_INVALID' USING ERRCODE = '23514';
  END;
  SELECT delivery_sequence INTO existing_sequence
    FROM content.publication_phase_outbox
   WHERE operation_id = p_operation_id AND phase = 'STAGE';
  requested_sequence := (command->>'deliverySequence')::bigint;
  IF COALESCE(jsonb_typeof(command) <> 'object'
      OR (SELECT count(*) FROM jsonb_object_keys(command)) <> 12
      OR NOT (command ?& ARRAY[
        'contractVersion', 'operationId', 'phase', 'parentRevision', 'targetRevision',
        'bundleId', 'bundleSha256', 'manifestSha256', 'realmId',
        'producerGeneration', 'participantGeneration', 'deliverySequence'
      ])
      OR (command->>'contractVersion')::integer <> 1
      OR (command->>'operationId')::uuid <> candidate.operation_id
      OR command->>'phase' <> 'STAGE'
      OR (command->>'parentRevision')::bigint <> candidate.parent_revision
      OR (command->>'targetRevision')::bigint <> candidate.target_revision
      OR command->>'bundleId' <> candidate.bundle_id
      OR command->>'bundleSha256' <> bundle.bundle_digest
      OR command->>'manifestSha256' <> bundle.manifest_digest
      OR (command->>'realmId')::uuid <> environment.realm_id
      OR (command->>'producerGeneration')::uuid <> environment.producer_generation
      OR (command->>'participantGeneration')::uuid <> environment.participant_generation
      OR requested_sequence <> COALESCE(
        existing_sequence, environment.last_delivery_sequence + 1), true) THEN
    RAISE EXCEPTION 'PUBLICATION_COMMAND_BINDING_CONFLICT' USING ERRCODE = '23505';
  END IF;

  IF candidate.operation_id IS NULL
      OR bundle.bundle_id IS NULL
      OR environment.singleton IS NULL
      OR reservation.operation_id IS DISTINCT FROM p_operation_id
      OR reservation.generation <> candidate.reservation_generation
      OR current_head <> candidate.parent_revision THEN
    RAISE EXCEPTION 'PUBLICATION_START_CONFLICT' USING ERRCODE = '23505';
  END IF;

  INSERT INTO content.publication_operation(
    operation_id,
    reservation_generation,
    parent_revision,
    target_revision,
    bundle_id,
    bundle_sha256,
    manifest_sha256,
    realm_id,
    producer_generation,
    participant_generation,
    state,
    last_sequence
  ) VALUES (
    candidate.operation_id,
    candidate.reservation_generation,
    candidate.parent_revision,
    candidate.target_revision,
    candidate.bundle_id,
    bundle.bundle_digest,
    bundle.manifest_digest,
    environment.realm_id,
    environment.producer_generation,
    environment.participant_generation,
    'STAGE_PENDING',
    requested_sequence
  ) ON CONFLICT DO NOTHING;

  INSERT INTO content.publication_phase_outbox(
    operation_id,
    phase,
    delivery_sequence,
    command_bytes,
    command_sha256
  ) VALUES (
    p_operation_id,
    'STAGE',
    requested_sequence,
    p_command_bytes,
    p_command_sha256
  ) ON CONFLICT DO NOTHING;

  IF NOT EXISTS (
    SELECT 1
      FROM content.publication_operation operation
      JOIN content.publication_phase_outbox outbox
        ON outbox.operation_id = operation.operation_id
       AND outbox.phase = 'STAGE'
     WHERE operation.operation_id = p_operation_id
       AND operation.state = 'STAGE_PENDING'
       AND outbox.delivery_sequence = requested_sequence
       AND outbox.command_sha256 = p_command_sha256
       AND outbox.command_bytes = p_command_bytes
  ) THEN
    RAISE EXCEPTION 'PUBLICATION_START_RETRY_CONFLICT' USING ERRCODE = '23505';
  END IF;
  IF existing_sequence IS NULL THEN
    -- Cursor producer tiến cùng transaction tạo STAGE; operation kế tiếp không
    -- được reset sequence vì Learning giữ một content-global cursor.
    UPDATE content.publication_environment
       SET last_delivery_sequence = requested_sequence
     WHERE singleton
       AND last_delivery_sequence = requested_sequence - 1;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_DELIVERY_SEQUENCE_CONFLICT' USING ERRCODE = '23505';
    END IF;
  END IF;
END $$;

CREATE FUNCTION content.claim_publication_delivery(
  p_operation_id uuid,
  p_phase text,
  p_lease_token uuid,
  p_capability bytea
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, content
AS $$
BEGIN
  IF NOT COALESCE(
      octet_length(p_capability) BETWEEN 32 AND 128
      AND sha256(p_capability) = (
        SELECT coordinator_capability_sha256
          FROM content.publication_environment
         WHERE singleton
      ),
      false) THEN
    RAISE EXCEPTION 'PUBLICATION_COORDINATOR_CAPABILITY_DENIED' USING ERRCODE = '42501';
  END IF;
  IF p_lease_token IS NULL THEN
    RAISE EXCEPTION 'PUBLICATION_DELIVERY_LEASE_TOKEN_REQUIRED' USING ERRCODE = '23502';
  END IF;
  UPDATE content.publication_phase_outbox
     SET lease_token = p_lease_token,
         lease_until = clock_timestamp() + interval '10 seconds'
   WHERE operation_id = p_operation_id
     AND phase = p_phase
     AND NOT delivered
     AND (lease_token IS NULL OR lease_until < clock_timestamp() OR lease_token = p_lease_token);
  IF NOT FOUND THEN
    RAISE EXCEPTION 'PUBLICATION_DELIVERY_LEASE_CONFLICT' USING ERRCODE = '55P03';
  END IF;
END $$;

CREATE FUNCTION content.accept_publication_receipt(
  p_operation_id uuid,
  p_phase text,
  p_receipt_bytes bytea,
  p_receipt_sha256 text,
  p_next_command_bytes bytea DEFAULT NULL,
  p_next_command_sha256 text DEFAULT NULL,
  p_lease_token uuid DEFAULT NULL,
  p_capability bytea DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, content
AS $$
DECLARE
  operation content.publication_operation%ROWTYPE;
  outbox content.publication_phase_outbox%ROWTYPE;
  environment content.publication_environment%ROWTYPE;
  receipt jsonb;
  next_command jsonb;
  expected_state text;
  next_phase text;
BEGIN
  IF p_phase NOT IN ('STAGE', 'FENCE', 'RELEASE', 'ABORT')
      OR p_receipt_sha256 !~ '^[0-9a-f]{64}$'
      OR octet_length(p_receipt_bytes) NOT BETWEEN 2 AND 8192 THEN
    RAISE EXCEPTION 'PUBLICATION_RECEIPT_INVALID' USING ERRCODE = '23514';
  END IF;

  SELECT * INTO operation
    FROM content.publication_operation
   WHERE operation_id = p_operation_id
   FOR UPDATE;
  SELECT * INTO outbox
    FROM content.publication_phase_outbox
   WHERE operation_id = p_operation_id AND phase = p_phase
   FOR UPDATE;
  SELECT * INTO environment
    FROM content.publication_environment
   WHERE singleton
   FOR UPDATE;
  IF NOT COALESCE(
      octet_length(p_capability) BETWEEN 32 AND 128
      AND sha256(p_capability) = environment.coordinator_capability_sha256,
      false) THEN
    RAISE EXCEPTION 'PUBLICATION_COORDINATOR_CAPABILITY_DENIED' USING ERRCODE = '42501';
  END IF;
  IF p_receipt_sha256 <> encode(sha256(p_receipt_bytes), 'hex') THEN
    RAISE EXCEPTION 'PUBLICATION_RECEIPT_DIGEST_MISMATCH' USING ERRCODE = '23514';
  END IF;

  BEGIN
    receipt := convert_from(p_receipt_bytes, 'UTF8')::jsonb;
  EXCEPTION WHEN OTHERS THEN
    RAISE EXCEPTION 'PUBLICATION_RECEIPT_JSON_INVALID' USING ERRCODE = '23514';
  END;
  IF COALESCE(jsonb_typeof(receipt) <> 'object'
      OR (SELECT count(*) FROM jsonb_object_keys(receipt)) <> 14
      OR NOT (receipt ?& ARRAY[
        'contractVersion', 'operationId', 'phase', 'state', 'parentRevision',
        'targetRevision', 'bundleId', 'bundleSha256', 'manifestSha256', 'realmId',
        'producerGeneration', 'participantGeneration', 'deliverySequence', 'commandSha256'
      ])
      OR (receipt->>'contractVersion')::integer <> 1
      OR (receipt->>'operationId')::uuid <> operation.operation_id
      OR receipt->>'phase' <> p_phase
      OR receipt->>'state' <> (CASE p_phase
        WHEN 'STAGE' THEN 'STAGED'
        WHEN 'FENCE' THEN 'FENCED'
        WHEN 'RELEASE' THEN 'RELEASED'
        ELSE 'ABORTED'
      END)
      OR (receipt->>'parentRevision')::bigint <> operation.parent_revision
      OR (receipt->>'targetRevision')::bigint <> operation.target_revision
      OR receipt->>'bundleId' <> operation.bundle_id
      OR receipt->>'bundleSha256' <> operation.bundle_sha256
      OR receipt->>'manifestSha256' <> operation.manifest_sha256
      OR (receipt->>'realmId')::uuid <> operation.realm_id
      OR (receipt->>'producerGeneration')::uuid <> operation.producer_generation
      OR (receipt->>'participantGeneration')::uuid <> operation.participant_generation
      OR (receipt->>'deliverySequence')::bigint <> outbox.delivery_sequence
      OR receipt->>'commandSha256' <> outbox.command_sha256, true) THEN
    RAISE EXCEPTION 'PUBLICATION_RECEIPT_BINDING_CONFLICT' USING ERRCODE = '23505';
  END IF;

  expected_state := CASE p_phase
    WHEN 'STAGE' THEN 'STAGE_PENDING'
    WHEN 'FENCE' THEN 'FENCE_PENDING'
    WHEN 'RELEASE' THEN 'RELEASE_PENDING'
    ELSE 'ABORT_PENDING'
  END;
  next_phase := CASE p_phase
    WHEN 'STAGE' THEN CASE WHEN operation.abort_requested THEN 'ABORT' ELSE 'FENCE' END
    WHEN 'FENCE' THEN CASE WHEN operation.abort_requested THEN 'ABORT' ELSE 'RELEASE' END
    ELSE NULL
  END;

  IF operation.operation_id IS NULL
      OR outbox.operation_id IS NULL
      OR operation.state <> expected_state THEN
    -- Exact receipt retry chỉ hội tụ khi stored bytes giống hệt.
    IF EXISTS (
      SELECT 1 FROM content.publication_phase_receipt
       WHERE operation_id = p_operation_id
         AND phase = p_phase
         AND receipt_bytes = p_receipt_bytes
         AND receipt_sha256 = p_receipt_sha256
         AND command_sha256 = outbox.command_sha256
    ) THEN
      RETURN;
    END IF;
    RAISE EXCEPTION 'PUBLICATION_RECEIPT_STATE_CONFLICT' USING ERRCODE = '23505';
  END IF;
  IF operation.state = expected_state
      AND (p_lease_token IS NULL
        OR outbox.lease_token IS NULL
        OR outbox.lease_until IS NULL
        OR outbox.lease_token IS DISTINCT FROM p_lease_token
        OR outbox.lease_until <= clock_timestamp()) THEN
    RAISE EXCEPTION 'PUBLICATION_DELIVERY_LEASE_DENIED' USING ERRCODE = '55P03';
  END IF;

  IF next_phase IS NOT NULL
      AND (p_next_command_sha256 !~ '^[0-9a-f]{64}$'
        OR octet_length(p_next_command_bytes) NOT BETWEEN 2 AND 8192) THEN
    RAISE EXCEPTION 'PUBLICATION_NEXT_COMMAND_INVALID' USING ERRCODE = '23514';
  END IF;
  IF next_phase IS NOT NULL
      AND p_next_command_sha256 <> encode(sha256(p_next_command_bytes), 'hex') THEN
    RAISE EXCEPTION 'PUBLICATION_NEXT_COMMAND_DIGEST_MISMATCH' USING ERRCODE = '23514';
  END IF;
  IF next_phase IS NOT NULL THEN
    BEGIN
      next_command := convert_from(p_next_command_bytes, 'UTF8')::jsonb;
    EXCEPTION WHEN OTHERS THEN
      RAISE EXCEPTION 'PUBLICATION_NEXT_COMMAND_JSON_INVALID' USING ERRCODE = '23514';
    END;
    IF COALESCE(jsonb_typeof(next_command) <> 'object'
        OR (SELECT count(*) FROM jsonb_object_keys(next_command)) <> 12
        OR NOT (next_command ?& ARRAY[
          'contractVersion', 'operationId', 'phase', 'parentRevision', 'targetRevision',
          'bundleId', 'bundleSha256', 'manifestSha256', 'realmId',
          'producerGeneration', 'participantGeneration', 'deliverySequence'
        ])
        OR (next_command->>'contractVersion')::integer <> 1
        OR (next_command->>'operationId')::uuid <> operation.operation_id
        OR next_command->>'phase' <> next_phase
        OR (next_command->>'parentRevision')::bigint <> operation.parent_revision
        OR (next_command->>'targetRevision')::bigint <> operation.target_revision
        OR next_command->>'bundleId' <> operation.bundle_id
        OR next_command->>'bundleSha256' <> operation.bundle_sha256
        OR next_command->>'manifestSha256' <> operation.manifest_sha256
        OR (next_command->>'realmId')::uuid <> operation.realm_id
        OR (next_command->>'producerGeneration')::uuid <> operation.producer_generation
        OR (next_command->>'participantGeneration')::uuid <> operation.participant_generation
        OR (next_command->>'deliverySequence')::bigint <> outbox.delivery_sequence + 1, true) THEN
      RAISE EXCEPTION 'PUBLICATION_NEXT_COMMAND_BINDING_CONFLICT' USING ERRCODE = '23505';
    END IF;
  END IF;
  IF next_phase IS NULL
      AND (p_next_command_bytes IS NOT NULL OR p_next_command_sha256 IS NOT NULL) THEN
    RAISE EXCEPTION 'PUBLICATION_UNEXPECTED_NEXT_COMMAND' USING ERRCODE = '23514';
  END IF;

  INSERT INTO content.publication_phase_receipt(
    operation_id,
    phase,
    receipt_bytes,
    receipt_sha256,
    command_sha256
  ) VALUES (
    p_operation_id,
    p_phase,
    p_receipt_bytes,
    p_receipt_sha256,
    outbox.command_sha256
  );
  UPDATE content.publication_phase_outbox
     SET delivered = true,
         lease_token = NULL,
         lease_until = NULL
   WHERE operation_id = p_operation_id AND phase = p_phase;

  IF p_phase = 'FENCE' AND NOT operation.abort_requested THEN
    -- PUB-04: canonical P+1 và RELEASE outbox cùng transaction này.
    UPDATE content.publication_canonical_head
       SET revision = operation.target_revision,
           bundle_id = operation.bundle_id,
           bundle_digest = operation.bundle_sha256
     WHERE singleton AND revision = operation.parent_revision;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_CANONICAL_HEAD_CONFLICT' USING ERRCODE = '23505';
    END IF;
  END IF;

  IF next_phase IS NOT NULL THEN
    UPDATE content.publication_environment
       SET last_delivery_sequence = outbox.delivery_sequence + 1
     WHERE singleton
       AND last_delivery_sequence = outbox.delivery_sequence;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_DELIVERY_SEQUENCE_CONFLICT' USING ERRCODE = '23505';
    END IF;
    INSERT INTO content.publication_phase_outbox(
      operation_id,
      phase,
      delivery_sequence,
      command_bytes,
      command_sha256
    ) VALUES (
      p_operation_id,
      next_phase,
      outbox.delivery_sequence + 1,
      p_next_command_bytes,
      p_next_command_sha256
    );
  END IF;

  IF p_phase = 'STAGE' THEN
    UPDATE content.publication_operation
       SET state = CASE WHEN abort_requested THEN 'ABORT_PENDING' ELSE 'FENCE_PENDING' END,
           last_sequence = outbox.delivery_sequence + 1
     WHERE operation_id = p_operation_id;
  ELSIF p_phase = 'FENCE' THEN
    UPDATE content.publication_operation
       SET state = CASE WHEN abort_requested THEN 'ABORT_PENDING' ELSE 'RELEASE_PENDING' END,
           last_sequence = outbox.delivery_sequence + 1
     WHERE operation_id = p_operation_id;
  ELSIF p_phase = 'RELEASE' THEN
    UPDATE content.publication_environment
       SET learning_ready_revision = operation.target_revision
     WHERE singleton AND learning_ready_revision = operation.parent_revision;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_READINESS_CONFLICT' USING ERRCODE = '23505';
    END IF;
    UPDATE content.publication_operation
       SET state = 'COMPLETE', completed_at = clock_timestamp()
     WHERE operation_id = p_operation_id;
    UPDATE content.publication_candidate_reservation
       SET operation_id = NULL,
           generation = generation + 1
     WHERE singleton
       AND operation_id = p_operation_id
       AND generation = operation.reservation_generation;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_RESERVATION_CONFLICT' USING ERRCODE = '23505';
    END IF;
  ELSIF p_phase = 'ABORT' THEN
    UPDATE content.publication_operation
       SET state = 'ABORTED', completed_at = clock_timestamp()
     WHERE operation_id = p_operation_id;
    UPDATE content.publication_candidate_reservation
       SET operation_id = NULL,
           generation = generation + 1
     WHERE singleton
       AND operation_id = p_operation_id
       AND generation = operation.reservation_generation;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'PUBLICATION_RESERVATION_CONFLICT' USING ERRCODE = '23505';
    END IF;
  END IF;
END $$;

CREATE FUNCTION content.request_publication_abort(
  p_operation_id uuid,
  p_capability bytea
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, content
AS $$
DECLARE
  operation content.publication_operation%ROWTYPE;
BEGIN
  SELECT * INTO operation
    FROM content.publication_operation
   WHERE operation_id = p_operation_id
   FOR UPDATE;
  IF NOT COALESCE(
      octet_length(p_capability) BETWEEN 32 AND 128
      AND sha256(p_capability) = (
        SELECT coordinator_capability_sha256
          FROM content.publication_environment
         WHERE singleton
      ),
      false) THEN
    RAISE EXCEPTION 'PUBLICATION_COORDINATOR_CAPABILITY_DENIED' USING ERRCODE = '42501';
  END IF;
  IF operation.state IN ('STAGE_PENDING', 'FENCE_PENDING') THEN
    -- Phase hiện tại có thể đã tới Learning nhưng ACK bị mất. Chỉ ghi intent;
    -- dispatcher resolve exact prefix trước, rồi ACK transition mới tạo ABORT.
    UPDATE content.publication_operation
       SET abort_requested = true
     WHERE operation_id = p_operation_id;
    RETURN;
  END IF;
  IF operation.state = 'ABORT_PENDING' AND operation.abort_requested THEN
    RETURN;
  END IF;
  IF operation.state NOT IN ('STAGE_PENDING', 'FENCE_PENDING') THEN
    -- FENCE receipt đã commit PUB-04: chỉ RELEASE recovery còn hợp lệ.
    RAISE EXCEPTION 'PUBLICATION_ABORT_PREFIX_OR_COMMIT_DENIED' USING ERRCODE = '23505';
  END IF;
END $$;

REVOKE ALL ON FUNCTION content.start_publication(uuid, bytea, text, bytea) FROM PUBLIC;
REVOKE ALL ON FUNCTION content.claim_publication_delivery(uuid, text, uuid, bytea) FROM PUBLIC;
REVOKE ALL ON FUNCTION content.accept_publication_receipt(
  uuid, text, bytea, text, bytea, text, uuid, bytea) FROM PUBLIC;
REVOKE ALL ON FUNCTION content.request_publication_abort(uuid, bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION content.start_publication(uuid, bytea, text, bytea) TO content_app;
GRANT EXECUTE ON FUNCTION content.claim_publication_delivery(uuid, text, uuid, bytea) TO content_app;
GRANT EXECUTE ON FUNCTION content.accept_publication_receipt(
  uuid, text, bytea, text, bytea, text, uuid, bytea) TO content_app;
GRANT EXECUTE ON FUNCTION content.request_publication_abort(uuid, bytea) TO content_app;

-- Provisioner điền đúng realm/generation và initial readiness cùng canonical head.
-- Migration không tự phát minh authority của môi trường.
