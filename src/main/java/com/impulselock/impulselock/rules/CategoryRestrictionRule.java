package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.util.List;

public class CategoryRestrictionRule extends AbstractSpendingRule {

    public static final String RULE_CODE = "CATEGORY_RESTRICTION";

    public CategoryRestrictionRule() {
        super(RULE_CODE, "Restricted spending category used");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
        String category = transaction.getCategory();
        if (category == null || category.isBlank()) return 0;

        List<String> restricted = userProfile.getRestrictedCategoryNames();
        if (restricted == null || restricted.isEmpty()) {
            return 0;
        }

        for (String r : restricted) {
            if (r != null && r.equalsIgnoreCase(category)) {
                return getRiskWeight(context);
            }
        }
        return 0;
    }
}
