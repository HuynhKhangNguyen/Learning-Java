CREATE TABLE content.failed_probe (id integer PRIMARY KEY);
INSERT INTO content.failed_probe VALUES (1);
SELECT 1 / 0;
