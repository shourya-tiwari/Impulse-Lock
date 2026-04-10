package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;

public class CategoryRestrictionRule extends AbstractSpendingRule {

    public CategoryRestrictionRule() {
        super(25.0, "Restricted spending category used");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        if ("LUXURY".equalsIgnoreCase(transaction.getCategory())) {
            return getRiskWeight();
        }
        return 0;
    }
}