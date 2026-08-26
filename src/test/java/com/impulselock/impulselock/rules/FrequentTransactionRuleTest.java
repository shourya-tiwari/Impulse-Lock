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

class FrequentTransactionRuleTest {

    private final FrequentTransactionRule rule = new FrequentTransactionRule();
    private final RuleConfig config = RuleTestFixtures.ruleConfig(FrequentTransactionRule.RULE_CODE, 30.0);

    @Test
    void doesNotFireBelowVelocityThreshold() {
        LocalDateTime now = LocalDateTime.now();
        Transaction priorTransaction = transactionAt(now.minusMinutes(5));
        Transaction current = transactionAt(now);

        // 1 prior + this one = 2, under the default threshold of 3.
        RuleContext context = RuleTestFixtures.contextWith(config, List.of(priorTransaction));

        assertThat(rule.evaluate(current, new User(), context)).isZero();
    }

    @Test
    void firesAtVelocityThreshold() {
        LocalDateTime now = LocalDateTime.now();
        Transaction first = transactionAt(now.minusMinutes(8));
        Transaction second = transactionAt(now.minusMinutes(3));
        Transaction current = transactionAt(now);

        // 2 prior + this one = 3, meeting the default threshold of 3.
        RuleContext context = RuleTestFixtures.contextWith(config, List.of(first, second));

        assertThat(rule.evaluate(current, new User(), context)).isEqualTo(30.0);
    }

    @Test
    void ignoresTransactionsOutsideTheVelocityWindow() {
        LocalDateTime now = LocalDateTime.now();
        Transaction tooOld = transactionAt(now.minusMinutes(15));
        Transaction recent = transactionAt(now.minusMinutes(2));
        Transaction current = transactionAt(now);

        // Only "recent" (2 min ago) is within the default 10-minute window alongside current;
        // "tooOld" (15 min ago) must not count -> 2 total, under the threshold of 3.
        RuleContext context = RuleTestFixtures.contextWith(config, List.of(tooOld, recent));

        assertThat(rule.evaluate(current, new User(), context)).isZero();
    }

    @Test
    void honorsConfiguredVelocityOverride() {
        RuleConfig customConfig = RuleTestFixtures.ruleConfig(FrequentTransactionRule.RULE_CODE, 30.0, true,
                java.util.Map.of("velocityWindowMinutes", 60, "velocityCountThreshold", 2));
        LocalDateTime now = LocalDateTime.now();
        Transaction earlier = transactionAt(now.minusMinutes(45));
        Transaction current = transactionAt(now);

        // Outside the default 10-minute window, but within the configured 60-minute one;
        // 1 prior + this one = 2, meeting the configured threshold of 2.
        RuleContext context = RuleTestFixtures.contextWith(customConfig, List.of(earlier));

        assertThat(rule.evaluate(current, new User(), context)).isEqualTo(30.0);
    }

    private Transaction transactionAt(LocalDateTime occurredAt) {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.TEN);
        transaction.setOccurredAt(occurredAt);
        return transaction;
    }
}
