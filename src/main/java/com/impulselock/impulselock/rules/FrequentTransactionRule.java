package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.time.LocalDateTime;

/**
 * Real velocity check, replacing V1/Phase 0/1's "amount > 1000" placeholder (see
 * docs/v1/rule-engine.md#frequenttransactionrule). Counts the user's transactions within the
 * last {@code velocityWindowMinutes} minutes - measured relative to the transaction being
 * evaluated, not wall-clock "now", so this is deterministic and testable - and fires if that
 * count (including the current transaction) reaches {@code velocityCountThreshold}.
 */
public class FrequentTransactionRule extends AbstractSpendingRule {

    public static final String RULE_CODE = "FREQUENT_TRANSACTION";
    private static final int DEFAULT_WINDOW_MINUTES = 10;
    private static final int DEFAULT_COUNT_THRESHOLD = 3;

    public FrequentTransactionRule() {
        super(RULE_CODE, "Multiple rapid transactions detected");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
        int windowMinutes = intParam(context, "velocityWindowMinutes", DEFAULT_WINDOW_MINUTES);
        int countThreshold = intParam(context, "velocityCountThreshold", DEFAULT_COUNT_THRESHOLD);

        LocalDateTime windowStart = transaction.getOccurredAt().minusMinutes(windowMinutes);

        long countInWindow = context.getRecentTransactions().stream()
                .filter(t -> !t.getOccurredAt().isBefore(windowStart) && !t.getOccurredAt().isAfter(transaction.getOccurredAt()))
                .count();

        // +1 for the transaction currently being evaluated, which isn't in "recent" yet.
        if (countInWindow + 1 >= countThreshold) {
            return getRiskWeight(context);
        }
        return 0;
    }
}
