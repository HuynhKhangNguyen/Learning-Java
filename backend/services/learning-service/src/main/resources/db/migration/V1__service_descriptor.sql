CREATE TABLE learning.service_descriptor (
  service_key text PRIMARY KEY CHECK (service_key = 'learning'),
  schema_revision integer NOT NULL CHECK (schema_revision = 1)
);
INSERT INTO learning.service_descriptor VALUES ('learning', 1);
-- Runtime reads this marker; only the migration role owns/changes it.
REVOKE INSERT, UPDATE, DELETE ON learning.service_descriptor FROM learning_app;
