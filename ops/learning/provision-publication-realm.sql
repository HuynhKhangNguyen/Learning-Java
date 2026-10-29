-- Forward-only R06b lab provisioning as learning_migrator after V4 and delivery realm.
-- Session backend_lab.provision_{realm,generation,run_id,content_generation,parent_revision}.
DO $$
DECLARE realm uuid; generation uuid; content_gen uuid; parent bigint;
BEGIN
  IF current_database()<>'learning_db' OR current_user<>'learning_migrator'
     OR current_setting('backend_lab.run_id')<>current_setting('backend_lab.provision_run_id') THEN
    RAISE EXCEPTION 'Owned Learning lab required';
  END IF;
  realm:=current_setting('backend_lab.provision_realm')::uuid;
  generation:=current_setting('backend_lab.provision_generation')::uuid;
  content_gen:=current_setting('backend_lab.provision_content_generation')::uuid;
  parent:=current_setting('backend_lab.provision_parent_revision')::bigint;
  IF parent<1 THEN RAISE EXCEPTION 'Invalid publication parent'; END IF;
  PERFORM pg_advisory_xact_lock(7406,2);
  IF NOT EXISTS(SELECT 1 FROM learning.delivery_environment WHERE singleton AND realm_id=realm AND db_generation=generation AND content_generation=content_gen AND ready AND NOT maintenance) THEN
    RAISE EXCEPTION 'Current delivery realm must be ready first';
  END IF;
  IF EXISTS(SELECT 1 FROM learning.publication_environment) OR EXISTS(SELECT 1 FROM learning.publication_head)
     OR EXISTS(SELECT 1 FROM learning.publication_operation) OR EXISTS(SELECT 1 FROM learning.publication_receipt)
     OR EXISTS(SELECT 1 FROM learning.content_replica) OR EXISTS(SELECT 1 FROM learning.publication_delivery_cursor) THEN
    RAISE EXCEPTION 'Publication state cannot be reprovisioned';
  END IF;
  INSERT INTO learning.publication_environment(singleton,realm_id,db_generation,content_generation,ready)
    VALUES(true,realm,generation,content_gen,true);
  INSERT INTO learning.publication_head(singleton,applied_revision) VALUES(true,parent);
  INSERT INTO learning.publication_delivery_cursor(stream_key,last_sequence) VALUES('content-global',0);
END $$;
