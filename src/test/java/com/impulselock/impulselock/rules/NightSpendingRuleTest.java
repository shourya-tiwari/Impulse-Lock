package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.RuleTestFixtures;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NightSpendingRuleTest {

    private final NightSpendingRule rule = new NightSpendingRule();
    private final RuleConfig config = RuleTestFixtures.ruleConfig(NightSpendingRule.RULE_CODE, 40.0);

    @Test
    void doesNotFireJustBeforeNightWindowStarts() {
        assertThat(rule.evaluate(transactionAt(22, 59), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(config))).isZero();
    }

    @Test
    void firesExactlyWhenNightWindowStarts() {
        assertThat(rule.evaluate(transactionAt(23, 0), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(config))).isEqualTo(40.0);
    }

    @Test
    void firesJustBeforeNightWindowEnds() {
        assertThat(rule.evaluate(transactionAt(5, 59), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(config))).isEqualTo(40.0);
    }

    @Test
    void doesNotFireExactlyWhenNightWindowEnds() {
        assertThat(rule.evaluate(transactionAt(6, 0), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(config))).isZero();
    }

    @Test
    void doesNotFireWhenNightSpendingIsAllowed() {
        User user = new User();
        user.setNightSpendingAllowed(true);

        assertThat(rule.evaluate(transactionAt(2, 0), user, RuleTestFixtures.contextWith(config))).isZero();
    }

    @Test
    void honorsConfiguredNightWindowOverride() {
        RuleConfig customConfig = RuleTestFixtures.ruleConfig(NightSpendingRule.RULE_CODE, 40.0, true,
                Map.of("nightStartHour", 21, "nightEndHour", 7));

        assertThat(rule.evaluate(transactionAt(21, 30), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(customConfig))).isEqualTo(40.0);
        // Would not have fired under the default 23:00 start.
        assertThat(rule.evaluate(transactionAt(21, 30), userDisallowingNightSpending(),
                RuleTestFixtures.contextWith(config))).isZero();
    }

    private User userDisallowingNightSpending() {
        User user = new User();
        user.setNightSpendingAllowed(false);
        return user;
    }

    private Transaction transactionAt(int hour, int minute) {
        Transaction transaction = new Transaction();
        transaction.setOccurredAt(LocalDateTime.now().withHour(hour).withMinute(minute).withSecond(0));
        return transaction;
    }
}
