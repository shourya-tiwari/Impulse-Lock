package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;

public class FrequentTransactionRule extends AbstractSpendingRule {

    private static final BigDecimal AMOUNT_THRESHOLD = BigDecimal.valueOf(1000);

    public FrequentTransactionRule() {
        super(30.0, "Multiple rapid transactions detected");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile) {
        // Simplified logic for Phase 1 demo - unchanged from V1 (see docs/v1/rule-engine.md).
        // Phase 2 replaces this with a real transaction-history velocity check.
        if (transaction.getAmount().compareTo(AMOUNT_THRESHOLD) > 0) {
            return getRiskWeight();
        }
        return 0;
    }
}
