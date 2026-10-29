-- Disposable DB-only fixture, never in the executable JAR.
CREATE TABLE content.transaction_probe (
  id text PRIMARY KEY, revision integer NOT NULL DEFAULT 0, payload text NOT NULL
);
