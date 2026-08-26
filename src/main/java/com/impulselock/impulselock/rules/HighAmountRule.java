package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public class HighAmountRule extends AbstractSpendingRule {

    public HighAmountRule() {
        super(70.0, "Transaction exceeds daily limit");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile) {
        if (transaction.getAmount().compareTo(userProfile.getDailyLimit()) > 0) {
            return getRiskWeight();
        }
        return 0;
    }
}
