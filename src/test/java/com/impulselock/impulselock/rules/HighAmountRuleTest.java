package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.engine.RuleContext;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.RuleTestFixtures;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule();
    private final RuleConfig config = RuleTestFixtures.ruleConfig(HighAmountRule.RULE_CODE, 70.0);

    @Test
    void doesNotFireWhenTodaysTotalEqualsLimit() {
        User user = userWithDailyLimit(1000);
        Transaction transaction = transactionWithAmount(1000, LocalDateTime.now());

        assertThat(rule.evaluate(transaction, user, RuleTestFixtures.contextWith(config))).isZero();
    }

    @Test
    void firesWhenTodaysTotalExceedsLimitByOneCent() {
        User user = userWithDailyLimit(1000);
        Transaction transaction = transactionWithAmount(1000.01, LocalDateTime.now());

        assertThat(rule.evaluate(transaction, user, RuleTestFixtures.contextWith(config))).isEqualTo(70.0);
    }

    @Test
    void aggregatesTodaysEarlierTransactionsTowardTheLimit() {
        User user = userWithDailyLimit(1000);
        LocalDateTime now = LocalDateTime.now();
        Transaction earlierToday = transactionWithAmount(600, now.minusHours(2));
        Transaction current = transactionWithAmount(500, now);

        RuleContext context = RuleTestFixtures.contextWith(config, List.of(earlierToday));

        // 600 (earlier today) + 500 (this transaction) = 1100 > 1000 limit.
        assertThat(rule.evaluate(current, user, context)).isEqualTo(70.0);
    }

    @Test
    void ignoresYesterdaysTransactionsWhenComputingTodaysTotal() {
        User user = userWithDailyLimit(1000);
        LocalDateTime now = LocalDateTime.now();
        Transaction yesterday = transactionWithAmount(900, now.minusDays(1));
        Transaction current = transactionWithAmount(500, now);

        RuleContext context = RuleTestFixtures.contextWith(config, List.of(yesterday));

        // 500 alone is under the 1000 limit; yesterday's 900 must not count toward today.
        assertThat(rule.evaluate(current, user, context)).isZero();
    }

    private User userWithDailyLimit(double limit) {
        User user = new User();
        user.setDailyLimit(BigDecimal.valueOf(limit));
        return user;
    }

    private Transaction transactionWithAmount(double amount, LocalDateTime occurredAt) {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(amount));
        transaction.setOccurredAt(occurredAt);
        return transaction;
    }
}
