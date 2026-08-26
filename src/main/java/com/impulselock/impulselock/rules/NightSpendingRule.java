package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public class NightSpendingRule extends AbstractSpendingRule {

    public static final String RULE_CODE = "NIGHT_SPENDING";
    private static final int DEFAULT_NIGHT_START_HOUR = 23;
    private static final int DEFAULT_NIGHT_END_HOUR = 6;

    public NightSpendingRule() {
        super(RULE_CODE, "Spending attempted during restricted night hours");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
        int hour = transaction.getOccurredAt().getHour();
        int nightStartHour = intParam(context, "nightStartHour", DEFAULT_NIGHT_START_HOUR);
        int nightEndHour = intParam(context, "nightEndHour", DEFAULT_NIGHT_END_HOUR);

        if (!userProfile.isNightSpendingAllowed() && (hour < nightEndHour || hour >= nightStartHour)) {
            return getRiskWeight(context);
        }
        return 0;
    }
}
