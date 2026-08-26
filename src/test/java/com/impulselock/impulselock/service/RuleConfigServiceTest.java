package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.repository.RuleConfigRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleConfigServiceTest {

    @Mock
    private RuleConfigRepository ruleConfigRepository;

    @Test
    void getByCodeThrowsWhenMissing() {
        RuleConfigService service = new RuleConfigService(ruleConfigRepository);
        when(ruleConfigRepository.findByRuleCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("MISSING")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findAllByCodeIndexesByRuleCode() {
        RuleConfigService service = new RuleConfigService(ruleConfigRepository);
        RuleConfig config = new RuleConfig();
        config.setRuleCode("HIGH_AMOUNT");
        config.setWeight(BigDecimal.valueOf(70));
        when(ruleConfigRepository.findAll()).thenReturn(List.of(config));

        Map<String, RuleConfig> result = service.findAllByCode();

        assertThat(result).containsOnlyKeys("HIGH_AMOUNT");
        assertThat(result.get("HIGH_AMOUNT").getWeight()).isEqualByComparingTo(BigDecimal.valueOf(70));
    }

    @Test
    void updateAppliesNewWeightEnabledAndParams() {
        RuleConfigService service = new RuleConfigService(ruleConfigRepository);
        RuleConfig existing = new RuleConfig();
        existing.setRuleCode("SENSITIVITY_LEVEL");
        existing.setWeight(BigDecimal.valueOf(20));
        existing.setEnabled(true);
        when(ruleConfigRepository.findByRuleCode("SENSITIVITY_LEVEL")).thenReturn(Optional.of(existing));
        when(ruleConfigRepository.save(any(RuleConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleConfig updated = service.update(
                "SENSITIVITY_LEVEL", BigDecimal.valueOf(25), false, Map.of("sensitivityThreshold", 9));

        assertThat(updated.getWeight()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getParams()).containsEntry("sensitivityThreshold", 9);
    }
}
