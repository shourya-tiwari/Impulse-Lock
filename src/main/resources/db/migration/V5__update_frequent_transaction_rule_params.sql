-- FREQUENT_TRANSACTION was seeded in Phase 0 with a placeholder param (amountThreshold) matching
-- V1's simplified "amount > 1000" check (see docs/v1/rule-engine.md#frequenttransactionrule).
-- Phase 2 replaces that rule's logic with a real velocity check, so its params change to match:
-- fire when this many transactions (velocityCountThreshold) occur within this many minutes
-- (velocityWindowMinutes) of each other, including the transaction being evaluated.

UPDATE rule_configs
SET params = JSON_OBJECT('velocityWindowMinutes', 10, 'velocityCountThreshold', 3)
WHERE rule_code = 'FREQUENT_TRANSACTION';
