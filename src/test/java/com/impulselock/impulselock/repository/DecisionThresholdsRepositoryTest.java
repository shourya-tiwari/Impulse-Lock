package com.impulselock.impulselock.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.DecisionThresholds;
import com.impulselock.impulselock.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies V3__add_decision_thresholds.sql seeded BLOCK/DELAY thresholds match V1's hardcoded
 * literals (80/40 - see docs/v1/rule-engine.md), so DecisionEngine's default behavior is
 * unchanged now that the thresholds are DB-configurable instead of Java constants.
 */
@SpringBootTest
class DecisionThresholdsRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private DecisionThresholdsRepository decisionThresholdsRepository;

    @Test
    void seedMigrationPopulatesV1EquivalentThresholds() {
        DecisionThresholds thresholds = decisionThresholdsRepository.findTopByOrderByIdAsc().orElseThrow();

        assertThat(thresholds.getBlockThreshold()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(thresholds.getDelayThreshold()).isEqualByComparingTo(new BigDecimal("40.00"));
    }
}
