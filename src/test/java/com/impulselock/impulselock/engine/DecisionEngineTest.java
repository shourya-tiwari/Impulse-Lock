package com.impulselock.impulselock.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.rules.SpendingRule;
import com.impulselock.impulselock.support.RuleTestFixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine();

    @Test
    void allowsWhenNoRuleFires() {
        SpendingRule rule = fixedRule("A", 0, "never");

        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(rule), contextForAll(List.of(rule)));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.getRiskScore()).isZero();
        assertThat(decision.getExplanation()).isEmpty();
        assertThat(decision.getTriggeredRules()).isEmpty();
    }

    @Test
    void delaysJustAtLowerThreshold() {
        SpendingRule rule = fixedRule("A", 40, "borderline");

        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(rule), contextForAll(List.of(rule)));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.DELAY);
        assertThat(decision.getRiskScore()).isEqualTo(40.0);
    }

    @Test
    void allowsJustBelowLowerThreshold() {
        SpendingRule rule = fixedRule("A", 39.99, "almost");

        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(rule), contextForAll(List.of(rule)));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.ALLOW);
    }

    @Test
    void blocksJustAtUpperThreshold() {
        SpendingRule ruleA = fixedRule("A", 70, "a");
        SpendingRule ruleB = fixedRule("B", 10, "b");
        List<SpendingRule> rules = List.of(ruleA, ruleB);

        Decision decision = engine.evaluate(new Transaction(), new User(), rules, contextForAll(rules));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.BLOCK);
        assertThat(decision.getRiskScore()).isEqualTo(80.0);
        assertThat(decision.getExplanation()).isEqualTo("a; b; ");
        assertThat(decision.getTriggeredRules()).hasSize(2);
    }

    @Test
    void capsRiskScoreAtOneHundred() {
        // V1/Phase 0/1 summed uncapped (see docs/v1/rule-engine.md#score-aggregation-caveat) -
        // Phase 2 introduces the cap.
        SpendingRule ruleA = fixedRule("A", 70, "a");
        SpendingRule ruleB = fixedRule("B", 40, "b");
        SpendingRule ruleC = fixedRule("C", 30, "c");
        List<SpendingRule> rules = List.of(ruleA, ruleB, ruleC);

        Decision decision = engine.evaluate(new Transaction(), new User(), rules, contextForAll(rules));

        assertThat(decision.getRiskScore()).isEqualTo(100.0);
        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.BLOCK);
    }

    @Test
    void skipsDisabledRulesEntirely() {
        SpendingRule rule = fixedRule("A", 90, "would block if enabled");
        RuleConfig disabledConfig = RuleTestFixtures.ruleConfig("A", 90, false, null);
        RuleContext context = new RuleContext(Map.of("A", disabledConfig), List.of(), RuleTestFixtures.defaultThresholds());

        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(rule), context);

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.getRiskScore()).isZero();
        assertThat(decision.getTriggeredRules()).isEmpty();
    }

    /**
     * Builds a context enabling every given rule. The fixed test rules below bake their risk
     * value directly into the lambda (bypassing {@code AbstractSpendingRule.getRiskWeight}), so
     * the config's own weight value is irrelevant here - only "enabled" matters to the engine.
     */
    private RuleContext contextForAll(List<SpendingRule> rules) {
        Map<String, RuleConfig> configs = new HashMap<>();
        for (SpendingRule rule : rules) {
            configs.put(rule.getRuleCode(), RuleTestFixtures.ruleConfig(rule.getRuleCode(), 0));
        }
        return new RuleContext(configs, List.of(), RuleTestFixtures.defaultThresholds());
    }

    private SpendingRule fixedRule(String ruleCode, double risk, String explanation) {
        return new SpendingRule() {
            @Override
            public double evaluate(Transaction transaction, User userProfile, RuleContext context) {
                return risk;
            }

            @Override
            public String getExplanation() {
                return explanation;
            }

            @Override
            public String getRuleCode() {
                return ruleCode;
            }
        };
    }
}
