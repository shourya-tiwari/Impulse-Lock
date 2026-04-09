package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public class FrequentTransactionRule extends AbstractSpendingRule {

    public FrequentTransactionRule() {
        super(30.0, "Multiple rapid transactions detected");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        // Simplified logic for Phase 1 demo
        if (transaction.getAmount() > 1000) {
            return getRiskWeight();
        }
        return 0;
    }
}