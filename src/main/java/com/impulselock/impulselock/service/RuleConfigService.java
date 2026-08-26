package com.impulselock.impulselock.service;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.repository.RuleConfigRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/update access to rule weights, enabled flags, and params (see
 * docs/v2/database-design.md#rule_configs). No admin controller calls {@link #update} yet -
 * that endpoint is Phase 3 (see docs/v2/tasks.md, Phase 2's own note); the method exists now
 * so the read side (used by {@code RuleContextFactory}) and the write side share one service.
 */
@Service
public class RuleConfigService {

    private final RuleConfigRepository ruleConfigRepository;

    public RuleConfigService(RuleConfigRepository ruleConfigRepository) {
        this.ruleConfigRepository = ruleConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<RuleConfig> findAll() {
        return ruleConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Map<String, RuleConfig> findAllByCode() {
        return findAll().stream().collect(Collectors.toMap(RuleConfig::getRuleCode, Function.identity()));
    }

    @Transactional(readOnly = true)
    public RuleConfig getByCode(String ruleCode) {
        return ruleConfigRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalStateException("No RuleConfig found for rule code: " + ruleCode));
    }

    @Transactional
    public RuleConfig update(String ruleCode, BigDecimal weight, boolean enabled, Map<String, Object> params) {
        RuleConfig config = getByCode(ruleCode);
        config.setWeight(weight);
        config.setEnabled(enabled);
        config.setParams(params);
        return ruleConfigRepository.save(config);
    }
}
