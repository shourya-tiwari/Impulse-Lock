package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

/**
 * {@code riskWeight} is no longer a constructor literal (see docs/v1/rule-engine.md) - it's
 * resolved per-evaluation from the {@link RuleContext} (backed by the {@code rule_configs}
 * table), via {@link #getRiskWeight(RuleContext)}. {@code ruleCode} and {@code explanation}
 * remain fixed per rule class: only weight/enabled/params are admin-configurable.
 */
public abstract class AbstractSpendingRule implements SpendingRule {

    protected final String ruleCode;
    protected final String explanation;

    protected AbstractSpendingRule(String ruleCode, String explanation) {
        this.ruleCode = ruleCode;
        this.explanation = explanation;
    }

    @Override
    public String getRuleCode() {
        return ruleCode;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }

    protected double getRiskWeight(RuleContext context) {
        return context.weightFor(ruleCode);
    }

    /** Reads an integer tunable from this rule's {@code RuleConfig.params}, or {@code defaultValue} if absent/not a number. */
    protected int intParam(RuleContext context, String key, int defaultValue) {
        java.util.Map<String, Object> params = context.paramsFor(ruleCode);
        Object value = params != null ? params.get(key) : null;
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    @Override
    public abstract double evaluate(Transaction transaction, User userProfile, RuleContext context);
}
