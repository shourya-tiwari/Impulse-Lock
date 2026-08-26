package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import org.junit.jupiter.api.Test;

class SensitivityLevelRuleTest {

    private final SensitivityLevelRule rule = new SensitivityLevelRule();

    @Test
    void doesNotFireBelowThreshold() {
        User user = new User();
        user.setSensitivityLevel(7);

        assertThat(rule.evaluate(new Transaction(), user)).isZero();
    }

    @Test
    void firesAtThreshold() {
        User user = new User();
        user.setSensitivityLevel(8);

        assertThat(rule.evaluate(new Transaction(), user)).isEqualTo(20.0);
    }
}
