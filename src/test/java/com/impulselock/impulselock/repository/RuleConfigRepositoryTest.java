package com.impulselock.impulselock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies V2__seed_roles_and_rule_configs.sql seeded weights match V1's hardcoded literals
 * exactly (see docs/v1/rule-engine.md) - the whole point of seeding these values on day one.
 */
@SpringBootTest
class RuleConfigRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RuleConfigRepository ruleConfigRepository;

    @Test
    void seedMigrationPopulatesAllFiveRulesWithV1EquivalentWeights() {
        assertWeight("HIGH_AMOUNT", "70.00");
        assertWeight("NIGHT_SPENDING", "40.00");
        assertWeight("FREQUENT_TRANSACTION", "30.00");
        assertWeight("CATEGORY_RESTRICTION", "25.00");
        assertWeight("SENSITIVITY_LEVEL", "20.00");
    }

    @Test
    void nightSpendingParamsMatchV1HardcodedWindow() {
        RuleConfig config = ruleConfigRepository.findByRuleCode("NIGHT_SPENDING").orElseThrow();

        assertThat(config.getParams()).containsEntry("nightStartHour", 23).containsEntry("nightEndHour", 6);
    }

    @Test
    void frequentTransactionParamsMatchTheRealVelocityCheckIntroducedInPhase2() {
        // V5__update_frequent_transaction_rule_params.sql replaces Phase 0's placeholder
        // amountThreshold param with the real velocity params FrequentTransactionRule now reads.
        RuleConfig config = ruleConfigRepository.findByRuleCode("FREQUENT_TRANSACTION").orElseThrow();

        assertThat(config.getParams())
                .containsEntry("velocityWindowMinutes", 10)
                .containsEntry("velocityCountThreshold", 3);
    }

    private void assertWeight(String ruleCode, String expectedWeight) {
        Optional<RuleConfig> config = ruleConfigRepository.findByRuleCode(ruleCode);

        assertThat(config).isPresent();
        assertThat(config.get().getWeight()).isEqualByComparingTo(new BigDecimal(expectedWeight));
        assertThat(config.get().isEnabled()).isTrue();
    }
}
