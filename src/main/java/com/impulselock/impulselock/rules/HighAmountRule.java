package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "Daily limit" now means calendar-day spend (midnight-to-midnight in server time), aggregated
 * across every transaction the user made "today" plus the one being evaluated - not a
 * single-transaction comparison like V1's version (see docs/v1/rule-engine.md#highamountrule).
 * Calendar day (not a rolling 24h window) was chosen to match how consumer banking apps usually
 * describe a daily limit ("resets at midnight") - see docs/v2/tasks.md Phase 2's decision point.
 */
public class HighAmountRule extends AbstractSpendingRule {

    public static final String RULE_CODE = "HIGH_AMOUNT";

    public HighAmountRule() {
        super(RULE_CODE, "Transaction exceeds daily limit");
    }

    @Override
    public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
        LocalDate today = transaction.getOccurredAt().toLocalDate();

        BigDecimal spentEarlierToday = context.getRecentTransactions().stream()
                .filter(t -> t.getOccurredAt().toLocalDate().equals(today))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedTotal = spentEarlierToday.add(transaction.getAmount());

        if (projectedTotal.compareTo(userProfile.getDailyLimit()) > 0) {
            return getRiskWeight(context);
        }
        return 0;
    }
}
