package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public interface SpendingRule {

    /**
     * Evaluates a transaction and returns a risk score (0–100)
     */
    double evaluate(Transaction transaction, User userProfile);

    /**
     * Explanation if the rule contributes to risk
     */
    String getExplanation();
}
