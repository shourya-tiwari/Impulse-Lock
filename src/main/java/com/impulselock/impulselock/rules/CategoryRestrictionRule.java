package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.model.Transaction;
import com.impulselock.impulselock.model.UserProfile;
import java.util.List;

public class CategoryRestrictionRule extends AbstractSpendingRule {

    public CategoryRestrictionRule() {
        super(25.0, "Restricted spending category used");
    }

    @Override
    public double evaluate(Transaction transaction, UserProfile userProfile) {
        String category = transaction.getCategory();
        if (category == null || category.isBlank()) return 0;

        List<String> restricted = userProfile.getRestrictedCategories();
        if (restricted == null || restricted.isEmpty()) {
            // Backward-compatible default
            return "LUXURY".equalsIgnoreCase(category) ? getRiskWeight() : 0;
        }

        for (String r : restricted) {
            if (r != null && r.equalsIgnoreCase(category)) {
                return getRiskWeight();
            }
        }
        return 0;
    }
}