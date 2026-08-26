-- Seed data. Rule weights below match V1's hardcoded literals exactly (see docs/v1/rule-engine.md)
-- so V2's out-of-the-box decision behavior is identical to V1 on day one. Phase 2 wires the rule
-- classes to actually read these rows (and adds real params for velocity/night-window/etc.);
-- until then this table exists but is not yet consulted by the rule engine.

INSERT INTO roles (name) VALUES
    ('ROLE_USER'),
    ('ROLE_ADMIN');

INSERT INTO rule_configs (rule_code, weight, enabled, params, updated_at) VALUES
    ('HIGH_AMOUNT', 70.00, TRUE, NULL, CURRENT_TIMESTAMP(3)),
    ('NIGHT_SPENDING', 40.00, TRUE, JSON_OBJECT('nightStartHour', 23, 'nightEndHour', 6), CURRENT_TIMESTAMP(3)),
    -- amountThreshold mirrors V1's simplified "amount > 1000" check; Phase 2 replaces this
    -- with a real velocity window (velocityWindowMinutes / velocityCountThreshold).
    ('FREQUENT_TRANSACTION', 30.00, TRUE, JSON_OBJECT('amountThreshold', 1000), CURRENT_TIMESTAMP(3)),
    ('CATEGORY_RESTRICTION', 25.00, TRUE, NULL, CURRENT_TIMESTAMP(3)),
    ('SENSITIVITY_LEVEL', 20.00, TRUE, JSON_OBJECT('sensitivityThreshold', 8), CURRENT_TIMESTAMP(3));
