package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule();

    @Test
    void doesNotFireWhenAmountEqualsLimit() {
        User user = userWithDailyLimit(1000);
        Transaction transaction = transactionWithAmount(1000);

        assertThat(rule.evaluate(transaction, user)).isZero();
    }

    @Test
    void firesWhenAmountExceedsLimitByOneCent() {
        User user = userWithDailyLimit(1000);
        Transaction transaction = transactionWithAmount(1000.01);

        assertThat(rule.evaluate(transaction, user)).isEqualTo(70.0);
    }

    @Test
    void doesNotFireWhenAmountIsBelowLimit() {
        User user = userWithDailyLimit(1000);
        Transaction transaction = transactionWithAmount(999.99);

        assertThat(rule.evaluate(transaction, user)).isZero();
    }

    private User userWithDailyLimit(double limit) {
        User user = new User();
        user.setDailyLimit(BigDecimal.valueOf(limit));
        return user;
    }

    private Transaction transactionWithAmount(double amount) {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(amount));
        return transaction;
    }
}
