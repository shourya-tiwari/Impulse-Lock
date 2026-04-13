package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public class NightSpendingRule extends AbstractSpendingRule {

    public NightSpendingRule() {
        super(40.0, "Spending attempted during restricted night hours");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        int hour = transaction.getTimestamp().getHour();

        if (!userProfile.isNightSpendingAllowed() && (hour < 6 || hour >= 23)) {
            return getRiskWeight();
        }
        return 0;
    }
}