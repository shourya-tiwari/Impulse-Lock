package com.impulselock.impulselock.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.DecisionThresholds;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.repository.DecisionThresholdsRepository;
import com.impulselock.impulselock.repository.RuleConfigRepository;
import com.impulselock.impulselock.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleContextFactoryTest {

    @Mock
    private RuleConfigRepository ruleConfigRepository;
    @Mock
    private DecisionThresholdsRepository decisionThresholdsRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private RuleContextFactory newFactory() {
        return new RuleContextFactory(ruleConfigRepository, decisionThresholdsRepository, transactionRepository);
    }

    private RuleConfig ruleConfig(String code, double weight) {
        RuleConfig config = new RuleConfig();
        config.setRuleCode(code);
        config.setWeight(BigDecimal.valueOf(weight));
        config.setEnabled(true);
        return config;
    }

    @Test
    void buildsAContextIndexingConfigsByRuleCodeAndCarryingThresholdsAndRecentTransactions() {
        RuleContextFactory factory = newFactory();
        when(ruleConfigRepository.findAll()).thenReturn(List.of(ruleConfig("HIGH_AMOUNT", 30), ruleConfig("NIGHT_SPENDING", 20)));
        DecisionThresholds thresholds = new DecisionThresholds();
        thresholds.setBlockThreshold(BigDecimal.valueOf(80));
        thresholds.setDelayThreshold(BigDecimal.valueOf(40));
        when(decisionThresholdsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(thresholds));
        Transaction recent = new Transaction();
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(recent));

        User user = new User();
        RuleContext context = factory.buildFor(user, LocalDateTime.now());

        assertThat(context.weightFor("HIGH_AMOUNT")).isEqualTo(30);
        assertThat(context.isEnabled("NIGHT_SPENDING")).isTrue();
        assertThat(context.getThresholds()).isSameAs(thresholds);
        assertThat(context.getRecentTransactions()).containsExactly(recent);
    }

    @Test
    void failsFastWhenDecisionThresholdsSeedIsMissing() {
        RuleContextFactory factory = newFactory();
        when(ruleConfigRepository.findAll()).thenReturn(List.of());
        when(decisionThresholdsRepository.findTopByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.buildFor(new User(), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decision_thresholds");
    }
}
