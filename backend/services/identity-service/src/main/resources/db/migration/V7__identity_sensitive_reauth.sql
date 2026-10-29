-- R04d: mở purpose riêng cho Identity; giữ nguyên V6 và reservation Learning.
-- Proof chỉ có một terminal effect: RESERVED hoặc CONSUMED, không hoàn tiền,
-- đổi owner/payload/TTL, hoặc dùng proof reset học để đổi mật khẩu.
ALTER TABLE identity.auth_reauth_grant
  DROP CONSTRAINT auth_reauth_grant_purpose_check,
  DROP CONSTRAINT auth_reauth_grant_state_check,
  DROP CONSTRAINT auth_reauth_grant_check1,
  ADD COLUMN consumed_operation_ref uuid UNIQUE,
  ADD COLUMN consumed_at timestamptz,
  ADD CONSTRAINT auth_reauth_consumed_owner
    FOREIGN KEY(consumed_operation_ref,user_id)
      REFERENCES identity.auth_operation(operation_id,target_user_id),
  ADD CONSTRAINT auth_reauth_purpose_v7
    CHECK(purpose IN ('LEARNING_RESET','LOGOUT_ALL','PASSWORD_CHANGE')),
  ADD CONSTRAINT auth_reauth_state_v7
    CHECK(state IN ('ISSUED','RESERVED','CONSUMED')),
  ADD CONSTRAINT auth_reauth_effect_v7 CHECK(
    (state='ISSUED' AND reservation_ref IS NULL AND reserved_at IS NULL
      AND consumed_operation_ref IS NULL AND consumed_at IS NULL) OR
    (state='RESERVED' AND purpose='LEARNING_RESET' AND reservation_ref IS NOT NULL
      AND reserved_at IS NOT NULL AND reserved_at>=issued_at AND reserved_at<expires_at
      AND consumed_operation_ref IS NULL AND consumed_at IS NULL) OR
    (state='CONSUMED' AND purpose IN ('LOGOUT_ALL','PASSWORD_CHANGE')
      AND consumed_operation_ref IS NOT NULL AND consumed_at IS NOT NULL
      AND consumed_at>=issued_at AND consumed_at<expires_at
      AND reservation_ref IS NULL AND reserved_at IS NULL));

CREATE OR REPLACE FUNCTION identity.guard_reauth_transition() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  -- Account → sessions → proof → operation → environment là thứ tự Java khóa.
  -- Binding bất biến; retry terminal chỉ SELECT, kể cả TTL đã hết sau commit.
  IF (to_jsonb(NEW)-ARRAY['state','reservation_ref','reserved_at','consumed_operation_ref','consumed_at'])
      IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['state','reservation_ref','reserved_at','consumed_operation_ref','consumed_at'])
     OR OLD.state<>'ISSUED' OR NEW.expires_at<=clock_timestamp()
     OR NOT EXISTS(
       SELECT 1 FROM identity.auth_session s JOIN identity.app_user u ON u.user_id=s.user_id
       JOIN identity.auth_environment e ON e.singleton AND e.realm_id=s.realm_id
       JOIN identity.delivery_environment d ON d.singleton AND d.realm_id=s.realm_id AND d.ready AND NOT d.maintenance
       WHERE s.session_ref=OLD.session_ref AND s.user_id=OLD.user_id AND s.context_id=OLD.context_id
         AND s.credential_epoch=OLD.credential_epoch AND s.realm_id=OLD.realm_id AND s.state='ACTIVE'
         AND u.state='ACTIVE' AND NOT u.operator_disabled AND NOT u.auth_transition_pending
         AND u.credential_epoch=s.credential_epoch
         AND s.issued_at+interval '12 hours'>clock_timestamp()
         AND s.last_interactive_at+interval '30 minutes'>clock_timestamp()
         AND EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=s.framework_id
           AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000)) THEN
    RAISE EXCEPTION 'Reauth immutable or expired' USING ERRCODE='23514';
  END IF;
  IF NEW.state='RESERVED' AND OLD.purpose='LEARNING_RESET' THEN
    RETURN NEW; -- Các CHECK bắt buộc reservation reference/time và không consume.
  END IF;
  -- Consume trước epoch/deny nhưng sau INSERT operation, cùng local transaction.
  -- Không chấp nhận maintenance operation, session scope hoặc key/context khác.
  IF NEW.state<>'CONSUMED' OR OLD.purpose NOT IN ('LOGOUT_ALL','PASSWORD_CHANGE')
     OR NOT EXISTS(SELECT 1 FROM identity.auth_operation o JOIN identity.auth_session s
       ON s.session_ref=OLD.session_ref
       WHERE o.operation_id=NEW.consumed_operation_ref AND o.target_user_id=OLD.user_id
         AND o.realm_id=OLD.realm_id AND o.scope='ACCOUNT' AND o.action=OLD.purpose
         AND o.credential_epoch=OLD.credential_epoch+1 AND o.state='PENDING_FENCES'
         AND o.initiator_kind='BROWSER' AND o.idempotency_key=OLD.operation_key
         AND o.initiating_context_id=OLD.context_id AND o.initiating_cookie_digest=s.cookie_digest
         AND o.initiating_csrf_digest=sha256(convert_to(s.csrf_token,'UTF8'))) THEN
    RAISE EXCEPTION 'Sensitive proof needs its exact operation' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;

CREATE FUNCTION identity.guard_reauth_consumed_effect() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
DECLARE op identity.auth_operation%ROWTYPE;
BEGIN
  -- Constraint trigger hoãn tới COMMIT: consume một mình không thể durable.
  -- Đủ local deny/outboxes là PENDING_FENCES, chưa phải remote ACK/COMPLETED.
  IF NEW.state<>'CONSUMED' THEN RETURN NEW; END IF;
  SELECT * INTO op FROM identity.auth_operation WHERE operation_id=NEW.consumed_operation_ref;
  IF NOT FOUND OR op.state<>'PENDING_FENCES' OR NEW.expires_at<=clock_timestamp()
     OR NOT EXISTS(SELECT 1 FROM identity.app_user u
       WHERE u.user_id=NEW.user_id AND u.state='ACTIVE' AND NOT u.operator_disabled
         AND u.auth_transition_pending AND u.auth_pending_operation=op.operation_id
         AND u.credential_epoch=NEW.credential_epoch+1)
     OR EXISTS(SELECT 1 FROM identity.auth_session s WHERE s.user_id=NEW.user_id
       AND s.realm_id=NEW.realm_id AND s.credential_epoch<=op.credential_epoch
       AND (s.state='ACTIVE' OR EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=s.framework_id)))
     OR EXISTS(SELECT 1 FROM identity.credential_token t WHERE t.user_id=NEW.user_id
       AND t.consumed_at IS NULL AND t.invalidated_at IS NULL)
     OR (SELECT count(*) FROM identity.auth_fence_intent i WHERE i.operation_id=op.operation_id
       AND i.command_digest=op.command_digest)<>2
     OR (SELECT count(*) FROM identity.auth_delivery_outbox d JOIN identity.delivery_environment e
       ON e.singleton AND e.ready AND NOT e.maintenance AND e.realm_id=d.realm_id
       WHERE d.operation_id=op.operation_id AND d.realm_id=NEW.realm_id
         AND d.command_digest=encode(op.command_digest,'hex')
         AND d.participant_generation=(CASE d.consumer WHEN 'content' THEN e.content_generation ELSE e.learning_generation END)
         AND d.envelope_digest=encode(sha256(d.command_bytes),'hex'))<>2 THEN
    RAISE EXCEPTION 'Sensitive proof effect must commit atomically' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER auth_reauth_consumed_effect_guard
  AFTER UPDATE ON identity.auth_reauth_grant DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION identity.guard_reauth_consumed_effect();
GRANT UPDATE(consumed_operation_ref,consumed_at) ON identity.auth_reauth_grant TO identity_app;
