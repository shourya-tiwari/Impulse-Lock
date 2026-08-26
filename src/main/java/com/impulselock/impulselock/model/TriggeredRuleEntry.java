package com.impulselock.impulselock.model;

import java.math.BigDecimal;

/**
 * One fired rule's contribution to a {@link Decision} - structured alternative to parsing
 * {@code Decision.explanation}'s semicolon-joined string (see docs/v1/rule-engine.md). Also the
 * shape persisted as JSON on {@code Transaction.triggeredRules} (see
 * docs/v2/database-design.md#transactions), so this same type is reused for both the API
 * response and the persisted column via {@code entity.converter.TriggeredRulesConverter}.
 */
public record TriggeredRuleEntry(String ruleCode, BigDecimal weight, String message) {
}
