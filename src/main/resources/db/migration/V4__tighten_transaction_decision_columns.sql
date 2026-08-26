-- Phase 0 left decision_type/risk_score/explanation/triggered_rules nullable because the JPA
-- write path didn't exist yet (see the header comment in V1__init_schema.sql). Phase 2 wires
-- that write path (TransactionService now populates all four on every save), so tighten them
-- here. Backfill first in case any rows were written by earlier phases without these columns set.
--
-- decision_type also moves from MySQL ENUM to VARCHAR + CHECK: Hibernate's @Enumerated(STRING)
-- mapping (VARCHAR) validates more reliably under ddl-auto=validate against a plain VARCHAR
-- column than against a vendor-specific ENUM type, which different JDBC drivers/versions can
-- report with varying JDBC type codes.

UPDATE transactions SET decision_type = 'ALLOW' WHERE decision_type IS NULL;
UPDATE transactions SET risk_score = 0 WHERE risk_score IS NULL;
UPDATE transactions SET explanation = '' WHERE explanation IS NULL;
UPDATE transactions SET triggered_rules = JSON_ARRAY() WHERE triggered_rules IS NULL;

ALTER TABLE transactions
    MODIFY COLUMN decision_type VARCHAR(10) NOT NULL,
    MODIFY COLUMN risk_score DECIMAL(5,2) NOT NULL,
    MODIFY COLUMN explanation TEXT NOT NULL,
    MODIFY COLUMN triggered_rules JSON NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_decision_type CHECK (decision_type IN ('ALLOW', 'DELAY', 'BLOCK'));
