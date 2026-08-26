package com.impulselock.impulselock.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.rules.SpendingRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionEngineTest {

    private final DecisionEngine engine = new DecisionEngine();

    @Test
    void allowsWhenNoRuleFires() {
        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(fixedRule(0, "never")));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.getRiskScore()).isZero();
        assertThat(decision.getExplanation()).isEmpty();
    }

    @Test
    void delaysJustAtLowerThreshold() {
        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(fixedRule(40, "borderline")));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.DELAY);
        assertThat(decision.getRiskScore()).isEqualTo(40.0);
    }

    @Test
    void allowsJustBelowLowerThreshold() {
        Decision decision = engine.evaluate(new Transaction(), new User(), List.of(fixedRule(39.99, "almost")));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.ALLOW);
    }

    @Test
    void blocksJustAtUpperThreshold() {
        Decision decision = engine.evaluate(new Transaction(), new User(),
                List.of(fixedRule(70, "a"), fixedRule(10, "b")));

        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.BLOCK);
        assertThat(decision.getRiskScore()).isEqualTo(80.0);
        assertThat(decision.getExplanation()).isEqualTo("a; b; ");
    }

    @Test
    void sumsUncappedAboveOneHundredMatchingV1Behavior() {
        // V1 had no cap on the summed risk score (see docs/v1/rule-engine.md#score-aggregation-caveat).
        // Phase 0 preserves that behavior exactly; Phase 2 introduces the 100-point cap.
        Decision decision = engine.evaluate(new Transaction(), new User(),
                List.of(fixedRule(70, "a"), fixedRule(40, "b"), fixedRule(30, "c")));

        assertThat(decision.getRiskScore()).isEqualTo(140.0);
        assertThat(decision.getDecisionType()).isEqualTo(DecisionType.BLOCK);
    }

    private SpendingRule fixedRule(double risk, String explanation) {
        return new SpendingRule() {
            @Override
            public double evaluate(Transaction transaction, User userProfile) {
                return risk;
            }

            @Override
            public String getExplanation() {
                return explanation;
            }
        };
    }
}
