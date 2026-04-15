package com.impulselock.impulselock.rules;


import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public interface SpendingRule {

    /**
     * Evaluates a transaction and returns a risk score (0–100)
     */
    double evaluate(Transaction transaction, UserProfile userProfile);

    /**
     * Explanation if the rule contributes to risk
     */
    String getExplanation();
}
