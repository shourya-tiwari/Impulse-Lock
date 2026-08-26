package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FrequentTransactionRuleTest {

    private final FrequentTransactionRule rule = new FrequentTransactionRule();

    @Test
    void doesNotFireAtThreshold() {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(1000));

        assertThat(rule.evaluate(transaction, new User())).isZero();
    }

    @Test
    void firesJustAboveThreshold() {
        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(1000.01));

        assertThat(rule.evaluate(transaction, new User())).isEqualTo(30.0);
    }
}
