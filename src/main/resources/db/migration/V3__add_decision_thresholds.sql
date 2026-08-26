-- Replaces DecisionEngine's hardcoded BLOCK/DELAY literals (80/40 - see docs/v1/rule-engine.md)
-- with a single configurable row, per docs/v2/database-design.md#rule_configs. Seeded with V1's
-- exact values so default behavior is unchanged; only an admin action (Phase 3) changes them
-- thereafter.

CREATE TABLE decision_thresholds (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    block_threshold DECIMAL(5,2) NOT NULL,
    delay_threshold DECIMAL(5,2) NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    CONSTRAINT chk_decision_thresholds_order CHECK (block_threshold > delay_threshold)
) ENGINE = InnoDB;

INSERT INTO decision_thresholds (block_threshold, delay_threshold, updated_at)
VALUES (80.00, 40.00, CURRENT_TIMESTAMP(3));
