package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public class SensitivityLevelRule extends AbstractSpendingRule {

    public SensitivityLevelRule() {
        super(20.0, "High user sensitivity level applied");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        if (userProfile.getSensitivityLevel() >= 8) {
            return getRiskWeight();
        }
        return 0;
    }
}