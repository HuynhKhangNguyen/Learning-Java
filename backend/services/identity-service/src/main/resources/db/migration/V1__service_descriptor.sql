CREATE TABLE identity.service_descriptor (
  service_key text PRIMARY KEY CHECK (service_key = 'identity'),
  schema_revision integer NOT NULL CHECK (schema_revision = 1)
);
INSERT INTO identity.service_descriptor VALUES ('identity', 1);
-- Runtime reads this marker; only the migration role owns/changes it.
REVOKE INSERT, UPDATE, DELETE ON identity.service_descriptor FROM identity_app;
