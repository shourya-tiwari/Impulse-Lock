package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NightSpendingRuleTest {

    private final NightSpendingRule rule = new NightSpendingRule();

    @Test
    void doesNotFireJustBeforeNightWindowStarts() {
        assertThat(rule.evaluate(transactionAt(22, 59), userDisallowingNightSpending())).isZero();
    }

    @Test
    void firesExactlyWhenNightWindowStarts() {
        assertThat(rule.evaluate(transactionAt(23, 0), userDisallowingNightSpending())).isEqualTo(40.0);
    }

    @Test
    void firesJustBeforeNightWindowEnds() {
        assertThat(rule.evaluate(transactionAt(5, 59), userDisallowingNightSpending())).isEqualTo(40.0);
    }

    @Test
    void doesNotFireExactlyWhenNightWindowEnds() {
        assertThat(rule.evaluate(transactionAt(6, 0), userDisallowingNightSpending())).isZero();
    }

    @Test
    void doesNotFireWhenNightSpendingIsAllowed() {
        User user = new User();
        user.setNightSpendingAllowed(true);

        assertThat(rule.evaluate(transactionAt(2, 0), user)).isZero();
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
