-- R04d forward proof store; không sửa checksum các migration đã qualified.
ALTER TABLE identity.auth_throttle DROP CONSTRAINT auth_throttle_bucket_kind_check;
ALTER TABLE identity.auth_throttle ADD CONSTRAINT auth_throttle_bucket_kind_v6
  CHECK(bucket_kind IN ('ACCOUNT','IP','ACTIVATE','RESET','REAUTH'));
ALTER TABLE identity.auth_session ADD CONSTRAINT auth_session_proof_binding
  UNIQUE(session_ref,user_id,context_id,credential_epoch,realm_id);

CREATE TABLE identity.auth_reauth_capacity (
  singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
  max_grants integer NOT NULL CHECK(max_grants BETWEEN 1 AND 100000)
);
INSERT INTO identity.auth_reauth_capacity VALUES(true,100000);
REVOKE INSERT,UPDATE,DELETE ON identity.auth_reauth_capacity FROM identity_app;

CREATE TABLE identity.auth_reauth_grant (
  proof_digest bytea PRIMARY KEY CHECK(octet_length(proof_digest)=32),
  grant_ref uuid NOT NULL UNIQUE,
  user_id uuid NOT NULL,
  session_ref uuid NOT NULL,
  context_id uuid NOT NULL,
  credential_epoch bigint NOT NULL CHECK(credential_epoch>0),
  realm_id uuid NOT NULL,
  purpose text NOT NULL CHECK(purpose='LEARNING_RESET'),
  operation_key uuid NOT NULL,
  intent_digest bytea NOT NULL CHECK(octet_length(intent_digest)=32),
  issued_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  state text NOT NULL DEFAULT 'ISSUED' CHECK(state IN ('ISSUED','RESERVED')),
  reservation_ref uuid UNIQUE,
  reserved_at timestamptz,
  FOREIGN KEY(session_ref,user_id,context_id,credential_epoch,realm_id)
    REFERENCES identity.auth_session(session_ref,user_id,context_id,credential_epoch,realm_id),
  CHECK(expires_at>issued_at AND expires_at<=issued_at+interval '5 minutes'),
  CHECK((state='ISSUED' AND reservation_ref IS NULL AND reserved_at IS NULL) OR
        (state='RESERVED' AND reservation_ref IS NOT NULL AND reserved_at>=issued_at AND reserved_at<expires_at))
);
CREATE INDEX auth_reauth_owner_idx ON identity.auth_reauth_grant(user_id,expires_at);

CREATE FUNCTION identity.guard_reauth_insert() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
DECLARE allowed integer;
BEGIN
  -- Account/session locks đã do Java callback lấy. Capacity lock sau chúng
  -- serialize toàn quota; expired history vẫn giữ, không refund/delete proof.
  PERFORM pg_advisory_xact_lock(7404,2);
  SELECT max_grants INTO allowed FROM identity.auth_reauth_capacity WHERE singleton;
  IF allowed IS NULL OR (SELECT count(*) FROM identity.auth_reauth_grant)>=allowed OR
     (SELECT count(*) FROM identity.auth_reauth_grant WHERE user_id=NEW.user_id AND expires_at>clock_timestamp())>=20 THEN
    RAISE EXCEPTION 'Reauth capacity exhausted' USING ERRCODE='P0001';
  END IF;
  IF NEW.state<>'ISSUED' OR NEW.issued_at>clock_timestamp()+interval '500 milliseconds'
     OR NEW.expires_at<=clock_timestamp() OR NOT EXISTS(
       SELECT 1 FROM identity.auth_session s JOIN identity.app_user u ON u.user_id=s.user_id
       JOIN identity.auth_environment e ON e.singleton AND e.realm_id=s.realm_id
       JOIN identity.delivery_environment d ON d.singleton AND d.realm_id=s.realm_id AND d.ready AND NOT d.maintenance
       WHERE s.session_ref=NEW.session_ref AND s.user_id=NEW.user_id AND s.context_id=NEW.context_id
         AND s.credential_epoch=NEW.credential_epoch AND s.realm_id=NEW.realm_id AND s.state='ACTIVE'
         AND u.state='ACTIVE' AND NOT u.operator_disabled AND NOT u.auth_transition_pending AND u.credential_epoch=s.credential_epoch
         AND s.issued_at+interval '12 hours'>clock_timestamp() AND s.last_interactive_at+interval '30 minutes'>clock_timestamp()
         AND EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=s.framework_id AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000)) THEN
    RAISE EXCEPTION 'Canonical reauth binding unavailable' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER auth_reauth_insert_guard BEFORE INSERT ON identity.auth_reauth_grant
  FOR EACH ROW EXECUTE FUNCTION identity.guard_reauth_insert();

CREATE FUNCTION identity.guard_reauth_transition() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog,identity AS $$
BEGIN
  -- Binding/TTL không đổi; RESERVED không UPDATE lần nữa, retry chỉ SELECT.
  IF (to_jsonb(NEW)-ARRAY['state','reservation_ref','reserved_at']) IS DISTINCT FROM
     (to_jsonb(OLD)-ARRAY['state','reservation_ref','reserved_at']) OR OLD.state<>'ISSUED' OR NEW.state<>'RESERVED'
     OR NEW.reservation_ref IS NULL OR NEW.reserved_at IS NULL OR NEW.expires_at<=clock_timestamp()
     OR NOT EXISTS(
       SELECT 1 FROM identity.auth_session s JOIN identity.app_user u ON u.user_id=s.user_id
       JOIN identity.auth_environment e ON e.singleton AND e.realm_id=s.realm_id
       JOIN identity.delivery_environment d ON d.singleton AND d.realm_id=s.realm_id AND d.ready AND NOT d.maintenance
       WHERE s.session_ref=OLD.session_ref AND s.user_id=OLD.user_id AND s.context_id=OLD.context_id
         AND s.credential_epoch=OLD.credential_epoch AND s.realm_id=OLD.realm_id AND s.state='ACTIVE'
         AND u.state='ACTIVE' AND NOT u.operator_disabled AND NOT u.auth_transition_pending AND u.credential_epoch=s.credential_epoch
         AND s.issued_at+interval '12 hours'>clock_timestamp() AND s.last_interactive_at+interval '30 minutes'>clock_timestamp()
         AND EXISTS(SELECT 1 FROM identity.spring_session f WHERE f.session_id=s.framework_id AND f.expiry_time>extract(epoch FROM clock_timestamp())*1000)) THEN
    RAISE EXCEPTION 'Reauth immutable or expired' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER auth_reauth_transition_guard BEFORE UPDATE ON identity.auth_reauth_grant
  FOR EACH ROW EXECUTE FUNCTION identity.guard_reauth_transition();
REVOKE UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON identity.auth_reauth_grant FROM identity_app;
GRANT UPDATE(state,reservation_ref,reserved_at) ON identity.auth_reauth_grant TO identity_app;
REVOKE TRUNCATE,REFERENCES,TRIGGER ON identity.auth_reauth_capacity FROM identity_app;
