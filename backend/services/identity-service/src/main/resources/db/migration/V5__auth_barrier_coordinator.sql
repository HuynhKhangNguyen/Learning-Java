-- R04d là forward migration: không đổi checksum V1–V4 đã được qualification.
-- Operation giữ immutable target/command; outbox là nguồn duy nhất cho durable ACK.
ALTER TABLE identity.auth_operation DROP CONSTRAINT auth_operation_state_check;
ALTER TABLE identity.auth_operation DROP CONSTRAINT auth_operation_action_check;
ALTER TABLE identity.auth_operation DROP CONSTRAINT auth_operation_check;
ALTER TABLE identity.auth_operation
  ALTER COLUMN initiating_cookie_digest DROP NOT NULL,
  ALTER COLUMN initiating_context_id DROP NOT NULL,
  ALTER COLUMN initiating_csrf_digest DROP NOT NULL,
  ADD COLUMN initiator_kind text NOT NULL DEFAULT 'BROWSER'
    CHECK (initiator_kind IN ('BROWSER','MAINTENANCE')),
  ADD COLUMN completed_at timestamptz,
  ADD CONSTRAINT auth_operation_action_v5 CHECK
    (action IN ('LOGOUT','EXPIRY','RESET','LOGOUT_ALL','PASSWORD_CHANGE','DISABLE','ENABLE','RECOVERY')),
  ADD CONSTRAINT auth_operation_scope_v5 CHECK
    ((scope='SESSION' AND target_session_ref IS NOT NULL AND action IN ('LOGOUT','EXPIRY')) OR
     (scope='ACCOUNT' AND target_session_ref IS NULL AND action IN ('RESET','LOGOUT_ALL','PASSWORD_CHANGE','DISABLE','ENABLE','RECOVERY'))),
  ADD CONSTRAINT auth_operation_initiator_v5 CHECK
    ((initiator_kind='BROWSER' AND initiating_cookie_digest IS NOT NULL AND initiating_context_id IS NOT NULL AND initiating_csrf_digest IS NOT NULL) OR
     (initiator_kind='MAINTENANCE' AND scope='ACCOUNT' AND initiating_cookie_digest IS NULL AND initiating_context_id IS NULL AND initiating_csrf_digest IS NULL)),
  ADD CONSTRAINT auth_operation_state_v5 CHECK
    ((state='PENDING_FENCES' AND completed_at IS NULL) OR (state='COMPLETED' AND completed_at IS NOT NULL)),
  ADD CONSTRAINT auth_operation_owner_pair UNIQUE(operation_id,target_user_id);
CREATE UNIQUE INDEX auth_account_operation_epoch_unique
  ON identity.auth_operation(target_user_id,credential_epoch) WHERE scope='ACCOUNT';

-- Marker gắn pending vào đúng operation/owner, không chỉ một boolean. ACK cũ
-- không được clear marker của credential epoch mới hơn; NULL cho legacy pending
-- buộc coordinator reconcile, không được đoán là đã hoàn tất.
ALTER TABLE identity.app_user ADD COLUMN auth_pending_operation uuid;
ALTER TABLE identity.app_user ADD CONSTRAINT auth_pending_operation_owner
  FOREIGN KEY(auth_pending_operation,user_id) REFERENCES identity.auth_operation(operation_id,target_user_id);
ALTER TABLE identity.app_user ADD CONSTRAINT auth_pending_marker_gate
  CHECK(auth_transition_pending OR auth_pending_operation IS NULL);
COMMENT ON COLUMN identity.auth_fence_intent.state IS
  'Snapshot local intent bất biến; dispatch/ACK hiện hành ở auth_delivery_outbox, completion ở auth_operation';

-- Chỉ cấp hai cột completion, không cấp UPDATE bindings hoặc DELETE history.
GRANT UPDATE(state,completed_at) ON identity.auth_operation TO identity_app;
REVOKE UPDATE,DELETE ON identity.credential_transition FROM identity_app;
GRANT UPDATE(state) ON identity.credential_transition TO identity_app;

CREATE FUNCTION identity.guard_auth_operation_completion() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
DECLARE env record; delivery record; command jsonb; ack jsonb;
        target_context uuid; target_state text; participant_count integer;
BEGIN
  -- No-op UPDATE cũng bị chặn: runtime chỉ được dùng guarded pending→completed,
  -- không biến quyền trên state thành quyền sửa lại hoặc hồi sinh operation.
  IF (to_jsonb(NEW)-ARRAY['state','completed_at']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['state','completed_at']) OR OLD.state<>'PENDING_FENCES'
     OR NEW.state<>'COMPLETED' OR NEW.completed_at IS NULL
     OR NEW.completed_at<OLD.created_at THEN
    RAISE EXCEPTION 'Auth operation immutable or invalid transition' USING ERRCODE='23514';
  END IF;
  SELECT * INTO env FROM identity.delivery_environment WHERE singleton FOR SHARE;
  IF NOT FOUND OR env.realm_id<>NEW.realm_id OR NOT env.ready OR env.maintenance THEN
    RAISE EXCEPTION 'Current auth environment unavailable' USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM identity.auth_environment WHERE singleton AND realm_id=NEW.realm_id
                AND topology_version=NEW.topology_version) THEN
    RAISE EXCEPTION 'Canonical realm mismatch' USING ERRCODE='23514';
  END IF;
  IF NEW.scope='SESSION' THEN
    SELECT context_id,state INTO target_context,target_state FROM identity.auth_session
      WHERE session_ref=NEW.target_session_ref AND user_id=NEW.target_user_id AND realm_id=NEW.realm_id;
    IF NOT FOUND OR target_state='ACTIVE' THEN
      RAISE EXCEPTION 'Canonical target not denied' USING ERRCODE='23514';
    END IF;
  ELSIF EXISTS(SELECT 1 FROM identity.auth_session WHERE user_id=NEW.target_user_id
                AND realm_id=NEW.realm_id AND credential_epoch<=NEW.credential_epoch AND state='ACTIVE') THEN
    RAISE EXCEPTION 'Canonical account still has active target' USING ERRCODE='23514';
  END IF;
  SELECT count(*) INTO participant_count FROM identity.auth_fence_intent
    WHERE operation_id=NEW.operation_id AND command_digest=NEW.command_digest;
  IF participant_count<>2 THEN
    RAISE EXCEPTION 'Mandatory intents incomplete' USING ERRCODE='23514';
  END IF;
  participant_count:=0;
  -- Lock exact outbox rows tới commit. ACK network đã kết thúc ở transaction
  -- trước; không remote call trong trigger hay khi giữ account/session locks.
  FOR delivery IN SELECT * FROM identity.auth_delivery_outbox
      WHERE operation_id=NEW.operation_id ORDER BY consumer FOR SHARE LOOP
    participant_count:=participant_count+1;
    IF delivery.delivery_status<>'DELIVERED' OR delivery.ack_bytes IS NULL
       OR delivery.realm_id<>NEW.realm_id OR delivery.command_digest<>encode(NEW.command_digest,'hex')
       OR delivery.participant_generation<>(CASE delivery.consumer WHEN 'content' THEN env.content_generation ELSE env.learning_generation END)
       OR delivery.envelope_digest<>encode(sha256(delivery.command_bytes),'hex') THEN
      RAISE EXCEPTION 'Durable current-generation auth ACK absent' USING ERRCODE='23514';
    END IF;
    command:=convert_from(delivery.command_bytes,'UTF8')::jsonb;
    ack:=convert_from(delivery.ack_bytes,'UTF8')::jsonb;
    IF jsonb_typeof(command)<>'object' OR jsonb_typeof(ack)<>'object'
       OR (SELECT count(*) FROM jsonb_object_keys(command))<>13
       OR (SELECT count(*) FROM jsonb_object_keys(ack))<>12
       OR command->'schemaVersion' IS DISTINCT FROM '1'::jsonb
       OR command->>'operationId' IS DISTINCT FROM NEW.operation_id::text
       OR command->>'audience' IS DISTINCT FROM delivery.consumer
       OR command->>'userId' IS DISTINCT FROM NEW.target_user_id::text
       OR command->>'scope' IS DISTINCT FROM NEW.scope OR command->>'action' IS DISTINCT FROM NEW.action
       OR command->>'sessionRef' IS DISTINCT FROM NEW.target_session_ref::text
       OR command->>'sessionContextId' IS DISTINCT FROM target_context::text
       OR command->>'epochFloor' IS DISTINCT FROM NEW.credential_epoch::text
       OR command->>'authRealmId' IS DISTINCT FROM NEW.realm_id::text
       OR command->>'topologyVersion' IS DISTINCT FROM NEW.topology_version
       OR command->>'participantGeneration' IS DISTINCT FROM delivery.participant_generation::text
       OR command->>'commandDigest' IS DISTINCT FROM delivery.command_digest
       OR ack->'schemaVersion' IS DISTINCT FROM '1'::jsonb OR ack->'durable' IS DISTINCT FROM 'true'::jsonb
       OR ack->>'receiver' IS DISTINCT FROM delivery.consumer
       OR ack->>'operationId' IS DISTINCT FROM NEW.operation_id::text
       OR ack->>'commandDigest' IS DISTINCT FROM delivery.command_digest
       OR ack->>'authRealmId' IS DISTINCT FROM NEW.realm_id::text
       OR ack->>'participantGeneration' IS DISTINCT FROM delivery.participant_generation::text
       OR ack->>'topologyVersion' IS DISTINCT FROM NEW.topology_version
       OR ack->>'scope' IS DISTINCT FROM NEW.scope
       OR ack->>'sessionRef' IS DISTINCT FROM NEW.target_session_ref::text
       OR ack->>'sessionContextId' IS DISTINCT FROM target_context::text
       OR ack->>'appliedFloor' IS NULL OR ack->>'appliedFloor' !~ '^[1-9][0-9]{0,18}$'
       OR (ack->>'appliedFloor')::bigint<NEW.credential_epoch THEN
      RAISE EXCEPTION 'Auth command or ACK binding mismatch' USING ERRCODE='23514';
    END IF;
  END LOOP;
  IF participant_count<>2 THEN
    RAISE EXCEPTION 'Both mandatory auth ACKs required' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER auth_operation_completion_guard BEFORE UPDATE ON identity.auth_operation
  FOR EACH ROW EXECUTE FUNCTION identity.guard_auth_operation_completion();

CREATE FUNCTION identity.guard_credential_transition_history() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  IF (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state')
     OR OLD.state<>'PENDING' OR NEW.state<>'COMPLETED' THEN
    RAISE EXCEPTION 'Credential transition history immutable' USING ERRCODE='23514';
  END IF;
  -- Disable/enable/recovery có lifecycle gate do owner sau điều phối. Chỉ RESET
  -- được đóng local credential transition ở auth barrier này, không giả journal ACK.
  IF NEW.action<>'RESET' OR NOT EXISTS(SELECT 1 FROM identity.auth_operation
      WHERE operation_id=NEW.operation_id AND target_user_id=NEW.user_id
        AND credential_epoch=NEW.credential_epoch AND action='RESET' AND state='COMPLETED') THEN
    RAISE EXCEPTION 'Credential transition needs its completed auth barrier' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER credential_transition_history_guard BEFORE UPDATE ON identity.credential_transition
  FOR EACH ROW EXECUTE FUNCTION identity.guard_credential_transition_history();

CREATE FUNCTION identity.guard_auth_pending_clear() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  IF OLD.auth_transition_pending AND NOT NEW.auth_transition_pending THEN
    -- Không mở gate chỉ bằng một boolean hoặc ACK thuộc epoch/operation cũ.
    -- Disable/enable/recovery vẫn cần owner journal/recovery, chưa có đường clear.
    IF OLD.auth_pending_operation IS NULL OR NEW.auth_pending_operation IS NOT NULL
       OR NEW.credential_epoch<>OLD.credential_epoch OR NEW.password_hash IS DISTINCT FROM OLD.password_hash
       OR NEW.state<>'ACTIVE' OR NEW.operator_disabled
       OR EXISTS(SELECT 1 FROM identity.credential_transition t WHERE t.user_id=NEW.user_id
           AND t.state='PENDING' AND t.action IN ('DISABLED','ACTIVE'))
       OR NOT EXISTS(SELECT 1 FROM identity.auth_operation o JOIN identity.auth_environment e
           ON e.singleton AND e.realm_id=o.realm_id
           WHERE o.operation_id=OLD.auth_pending_operation AND o.target_user_id=NEW.user_id
             AND o.credential_epoch=NEW.credential_epoch AND o.state='COMPLETED'
             AND o.action IN ('RESET','LOGOUT_ALL','PASSWORD_CHANGE')
             AND (SELECT count(*) FROM identity.auth_delivery_outbox d
               JOIN identity.delivery_environment env ON env.singleton AND env.ready AND NOT env.maintenance
                 AND env.realm_id=d.realm_id AND env.realm_id=o.realm_id
               WHERE d.operation_id=o.operation_id AND d.delivery_status='DELIVERED'
                 AND d.participant_generation=(CASE d.consumer WHEN 'content' THEN env.content_generation ELSE env.learning_generation END))=2) THEN
      RAISE EXCEPTION 'Current completed auth barrier required to clear pending' USING ERRCODE='23514';
    END IF;
  ELSIF NEW.auth_pending_operation IS DISTINCT FROM OLD.auth_pending_operation THEN
    IF NEW.auth_pending_operation IS NULL OR NOT NEW.auth_transition_pending
       OR NOT EXISTS(SELECT 1 FROM identity.auth_operation o
           WHERE o.operation_id=NEW.auth_pending_operation AND o.target_user_id=NEW.user_id
             AND o.credential_epoch=NEW.credential_epoch AND o.state='PENDING_FENCES') THEN
      RAISE EXCEPTION 'Pending marker must bind current operation' USING ERRCODE='23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER auth_pending_clear_guard BEFORE UPDATE ON identity.app_user
  FOR EACH ROW EXECUTE FUNCTION identity.guard_auth_pending_clear();
