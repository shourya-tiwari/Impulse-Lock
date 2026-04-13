package com.impulselock.impulselock.config;

import com.impulselock.impulselock.engine.DecisionEngine;
import com.impulselock.impulselock.rules.CategoryRestrictionRule;
import com.impulselock.impulselock.rules.FrequentTransactionRule;
import com.impulselock.impulselock.rules.HighAmountRule;
import com.impulselock.impulselock.rules.NightSpendingRule;
import com.impulselock.impulselock.rules.SensitivityLevelRule;
import com.impulselock.impulselock.rules.SpendingRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuleEngineConfig {

    @Bean
    public DecisionEngine decisionEngine() {
        return new DecisionEngine();
    }

    @Bean
    public SpendingRule highAmountRule() {
        return new HighAmountRule();
    }

    @Bean
    public SpendingRule nightSpendingRule() {
        return new NightSpendingRule();
    }

    @Bean
    public SpendingRule frequentTransactionRule() {
        return new FrequentTransactionRule();
    }

    @Bean
    public SpendingRule categoryRestrictionRule() {
        return new CategoryRestrictionRule();
    }

    @Bean
    public SpendingRule sensitivityLevelRule() {
        return new SensitivityLevelRule();
    }
}
