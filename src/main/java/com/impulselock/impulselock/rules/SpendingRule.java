package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public interface SpendingRule {

    /**
     * Evaluates a transaction and returns a risk score (0–100). {@code context} carries this
     * rule's current weight/enabled/params plus the user's recent transaction history - see
     * {@link RuleContext}.
     */
    double evaluate(Transaction transaction, User userProfile, RuleContext context);

    /**
     * Explanation if the rule contributes to risk
     */
    String getExplanation();

    /** Stable identifier matching a {@code rule_configs.rule_code} row, e.g. {@code "HIGH_AMOUNT"}. */
    String getRuleCode();
}
