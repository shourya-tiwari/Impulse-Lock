package com.impulselock.impulselock.engine;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.model.Decision;
import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.model.TriggeredRuleEntry;
import com.impulselock.impulselock.rules.SpendingRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DecisionEngine {

    private static final double MAX_RISK_SCORE = 100.0;

    public Decision evaluate(Transaction transaction,
                             User userProfile,
                             List<SpendingRule> rules,
                             RuleContext context) {

        double totalRisk = 0;
        StringBuilder explanation = new StringBuilder();
        List<TriggeredRuleEntry> triggeredRules = new ArrayList<>();

        for (SpendingRule rule : rules) {
            if (!context.isEnabled(rule.getRuleCode())) {
                continue;
            }

            double risk = rule.evaluate(transaction, userProfile, context);
            if (risk > 0) {
                totalRisk += risk;
                explanation.append(rule.getExplanation()).append("; ");
                triggeredRules.add(new TriggeredRuleEntry(rule.getRuleCode(), BigDecimal.valueOf(risk), rule.getExplanation()));
            }
        }

        // V1/Phase 0/1 summed uncapped (see docs/v1/rule-engine.md#score-aggregation-caveat) -
        // Phase 2 caps the reported score at 100 while still using the uncapped total, if higher,
        // to decide BLOCK vs DELAY (capping only changes what's reported, never the decision).
        double cappedRisk = Math.min(totalRisk, MAX_RISK_SCORE);

        Decision decision = new Decision();
        decision.setRiskScore(cappedRisk);
        decision.setExplanation(explanation.toString());
        decision.setTriggeredRules(triggeredRules);

        if (totalRisk >= context.getThresholds().getBlockThreshold().doubleValue()) {
            decision.setDecisionType(DecisionType.BLOCK);
        } else if (totalRisk >= context.getThresholds().getDelayThreshold().doubleValue()) {
            decision.setDecisionType(DecisionType.DELAY);
        } else {
            decision.setDecisionType(DecisionType.ALLOW);
        }

        return decision;
    }
}
