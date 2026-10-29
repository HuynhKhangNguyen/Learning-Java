CREATE TABLE identity.failed_probe (id integer PRIMARY KEY);
INSERT INTO identity.failed_probe VALUES (1);
SELECT 1 / 0;
