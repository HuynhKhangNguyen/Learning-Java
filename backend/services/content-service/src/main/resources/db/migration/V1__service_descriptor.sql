CREATE TABLE content.service_descriptor (
  service_key text PRIMARY KEY CHECK (service_key = 'content'),
  schema_revision integer NOT NULL CHECK (schema_revision = 1)
);
INSERT INTO content.service_descriptor VALUES ('content', 1);
-- Runtime reads this marker; only the migration role owns/changes it.
REVOKE INSERT, UPDATE, DELETE ON content.service_descriptor FROM content_app;
