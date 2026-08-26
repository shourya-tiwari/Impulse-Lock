package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public class SensitivityLevelRule extends AbstractSpendingRule {

    public static final String RULE_CODE = "SENSITIVITY_LEVEL";
    private static final int DEFAULT_THRESHOLD = 8;

    public SensitivityLevelRule() {
        super(RULE_CODE, "High user sensitivity level applied");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
        int threshold = intParam(context, "sensitivityThreshold", DEFAULT_THRESHOLD);
        if (userProfile.getSensitivityLevel() >= threshold) {
            return getRiskWeight(context);
        }
        return 0;
    }
}
