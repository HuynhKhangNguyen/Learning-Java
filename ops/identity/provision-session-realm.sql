-- Operator only, forward provision once. PGOPTIONS must supply the expected
-- backend_lab.provision_realm and backend_lab.provision_run_id UUIDs.
-- Never invoked by the app; mismatching an existing realm is an error, not reset.
BEGIN;
DO $$
DECLARE expected_realm uuid := current_setting('backend_lab.provision_realm')::uuid;
        expected_run uuid := current_setting('backend_lab.provision_run_id')::uuid;
        existing_realm uuid;
BEGIN
  IF current_database()<>'identity_db' OR current_user<>'identity_migrator'
     OR current_setting('backend_lab.run_id')::uuid<>expected_run THEN
    RAISE EXCEPTION 'Own operator environment required';
  END IF;
  LOCK TABLE identity.auth_environment IN EXCLUSIVE MODE;
  SELECT realm_id INTO existing_realm FROM identity.auth_environment WHERE singleton;
  IF existing_realm IS NOT NULL AND existing_realm<>expected_realm THEN
    RAISE EXCEPTION 'Existing realm cannot be reset';
  END IF;
  IF existing_realm IS NULL THEN
    IF EXISTS(SELECT 1 FROM identity.auth_session) OR EXISTS(SELECT 1 FROM identity.auth_preauth)
       OR EXISTS(SELECT 1 FROM identity.auth_operation) THEN
      RAISE EXCEPTION 'Nonempty auth state requires recovery protocol';
    END IF;
    INSERT INTO identity.auth_environment VALUES (true,expected_realm,'r1-static-v1');
  END IF;
END $$;
COMMIT;
