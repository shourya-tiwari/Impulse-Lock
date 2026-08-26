package com.impulselock.impulselock.support;

import com.impulselock.impulselock.entity.DecisionThresholds;
import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.engine.RuleContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Hand-built {@code RuleConfig}/{@code RuleContext} fixtures so rule and engine unit tests need
 * no Spring context, mirroring the plain-POJO fixture style already used elsewhere in the test
 * suite (see docs/v2/testing-strategy.md#unit-tests-no-spring-context-fastest-tier).
 */
public final class RuleTestFixtures {

    private RuleTestFixtures() {
    }

    public static RuleConfig ruleConfig(String ruleCode, double weight) {
        return ruleConfig(ruleCode, weight, true, null);
    }

    public static RuleConfig ruleConfig(String ruleCode, double weight, boolean enabled, Map<String, Object> params) {
        RuleConfig config = new RuleConfig();
        config.setRuleCode(ruleCode);
        config.setWeight(BigDecimal.valueOf(weight));
        config.setEnabled(enabled);
        config.setParams(params);
        return config;
    }

    public static RuleContext contextWith(RuleConfig config) {
        return contextWith(config, List.of());
    }

    public static RuleContext contextWith(RuleConfig config, List<Transaction> recentTransactions) {
        return new RuleContext(Map.of(config.getRuleCode(), config), recentTransactions, defaultThresholds());
    }

    public static DecisionThresholds defaultThresholds() {
        DecisionThresholds thresholds = new DecisionThresholds();
        thresholds.setBlockThreshold(BigDecimal.valueOf(80));
        thresholds.setDelayThreshold(BigDecimal.valueOf(40));
        return thresholds;
    }
}
