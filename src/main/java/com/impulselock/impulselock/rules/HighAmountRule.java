package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public class HighAmountRule extends AbstractSpendingRule {

    public HighAmountRule() {
        super(70.0, "Transaction exceeds daily limit");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        if (transaction.getAmount() > userProfile.getDailyLimit()) {
            return getRiskWeight();
        }
        return 0;
    }
}