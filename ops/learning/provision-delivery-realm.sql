-- Forward-only owned-lab delivery provisioning as learning_migrator, after own migrations/auth realm.
-- Session backend_lab.provision_{realm,generation,run_id,identity_generation,content_generation,learning_generation}.
DO $$
DECLARE realm uuid; generation uuid; identity_gen uuid; content_gen uuid; learning_gen uuid;
        saved learning.delivery_environment%ROWTYPE;
BEGIN
  IF current_database()<>'learning_db' OR current_user<>'learning_migrator'
     OR current_setting('backend_lab.run_id')<>current_setting('backend_lab.provision_run_id') THEN
    RAISE EXCEPTION 'Owned service lab required';
  END IF;
  realm:=current_setting('backend_lab.provision_realm')::uuid;
  generation:=current_setting('backend_lab.provision_generation')::uuid;
  identity_gen:=current_setting('backend_lab.provision_identity_generation')::uuid;
  content_gen:=current_setting('backend_lab.provision_content_generation')::uuid;
  learning_gen:=current_setting('backend_lab.provision_learning_generation')::uuid;
  IF generation<>learning_gen THEN RAISE EXCEPTION 'Own generation mismatch'; END IF;
  PERFORM pg_advisory_xact_lock(7402,3);
  IF NOT EXISTS(SELECT 1 FROM learning.auth_environment WHERE singleton AND realm_id=realm AND db_generation=generation AND ready) THEN
    RAISE EXCEPTION 'Current own auth realm must be provisioned first';
  END IF;
  SELECT * INTO saved FROM learning.delivery_environment WHERE singleton FOR UPDATE;
  IF FOUND THEN
    IF saved.realm_id<>realm OR saved.db_generation<>generation OR saved.identity_generation<>identity_gen
       OR saved.content_generation<>content_gen OR saved.learning_generation<>learning_gen OR NOT saved.ready OR saved.maintenance THEN
      RAISE EXCEPTION 'Existing delivery security state cannot be reprovisioned';
    END IF;
  ELSE
    IF EXISTS(SELECT 1 FROM learning.delivery_producer_stream) OR EXISTS(SELECT 1 FROM learning.delivery_outbox)
       OR EXISTS(SELECT 1 FROM learning.delivery_receiver_stream) OR EXISTS(SELECT 1 FROM learning.delivery_inbox)
       OR EXISTS(SELECT 1 FROM learning.delivery_quarantine) OR EXISTS(SELECT 1 FROM learning.delivery_recovery_receipt)
       OR EXISTS(SELECT 1 FROM learning.auth_delivery_outbox) THEN
      RAISE EXCEPTION 'Cannot provision over retained delivery state';
    END IF;
    INSERT INTO learning.delivery_environment(singleton,realm_id,db_generation,identity_generation,content_generation,learning_generation,ready)
      VALUES(true,realm,generation,identity_gen,content_gen,learning_gen,true);
  END IF;
END $$;

