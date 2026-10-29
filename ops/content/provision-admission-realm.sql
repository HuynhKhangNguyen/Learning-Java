-- Explicit disposable lab provisioning, run as content_migrator after migrations.
-- Session settings backend_lab.provision_{realm,generation,run_id} are required.
-- No arbitrary DSN, production restore, realm reset or app-role elevation.
DO $$
DECLARE realm uuid; generation uuid; saved content.auth_environment%ROWTYPE;
BEGIN
  IF current_database()<>'content_db' OR current_user<>'content_migrator'
     OR current_setting('backend_lab.run_id')<>current_setting('backend_lab.provision_run_id') THEN
    RAISE EXCEPTION 'Owned Content lab required';
  END IF;
  realm:=current_setting('backend_lab.provision_realm')::uuid;
  generation:=current_setting('backend_lab.provision_generation')::uuid;
  PERFORM pg_advisory_xact_lock(7404,2);
  SELECT * INTO saved FROM content.auth_environment WHERE singleton FOR UPDATE;
  IF FOUND THEN
    IF saved.realm_id<>realm OR saved.db_generation<>generation OR NOT saved.ready
       OR saved.topology_version<>'r1-static-v1' THEN
      RAISE EXCEPTION 'Existing receiver state cannot be reprovisioned';
    END IF;
  ELSE
    IF EXISTS(SELECT 1 FROM content.auth_account_gate) OR EXISTS(SELECT 1 FROM content.auth_session_fence)
       OR EXISTS(SELECT 1 FROM content.auth_command_receipt) OR EXISTS(SELECT 1 FROM content.auth_admission_use) THEN
      RAISE EXCEPTION 'Cannot provision over retained auth state';
    END IF;
    INSERT INTO content.auth_environment(singleton,realm_id,db_generation,topology_version,ready)
      VALUES(true,realm,generation,'r1-static-v1',true);
  END IF;
END $$;
