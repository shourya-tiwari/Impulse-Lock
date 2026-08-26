package com.impulselock.impulselock.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.support.RuleTestFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitivityLevelRuleTest {

    private final SensitivityLevelRule rule = new SensitivityLevelRule();
    private final RuleConfig config = RuleTestFixtures.ruleConfig(SensitivityLevelRule.RULE_CODE, 20.0);

    @Test
    void doesNotFireBelowThreshold() {
        User user = new User();
        user.setSensitivityLevel(7);

        assertThat(rule.evaluate(new Transaction(), user, RuleTestFixtures.contextWith(config))).isZero();
    }

    @Test
    void firesAtThreshold() {
        User user = new User();
        user.setSensitivityLevel(8);

        assertThat(rule.evaluate(new Transaction(), user, RuleTestFixtures.contextWith(config))).isEqualTo(20.0);
    }

    @Test
    void honorsConfiguredThresholdOverride() {
        RuleConfig customConfig = RuleTestFixtures.ruleConfig(SensitivityLevelRule.RULE_CODE, 20.0, true,
                Map.of("sensitivityThreshold", 5));
        User user = new User();
        user.setSensitivityLevel(6);

        assertThat(rule.evaluate(new Transaction(), user, RuleTestFixtures.contextWith(customConfig)))
                .isEqualTo(20.0);
    }
}
