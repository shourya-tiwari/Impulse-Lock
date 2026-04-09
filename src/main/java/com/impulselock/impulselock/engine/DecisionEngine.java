package com.impulselock.impulselock.engine;

import com.impulselock.impulselock.model.*;
import com.impulselock.impulselock.rules.SpendingRule;

import java.util.List;

public class DecisionEngine {

    public Decision evaluate(Transaction transaction,
                             UserProfile userProfile,
                             List<SpendingRule> rules) {

        double totalRisk = 0;
        StringBuilder explanation = new StringBuilder();

        for (SpendingRule rule : rules) {
            double risk = rule.evaluate(transaction, userProfile);
            if (risk > 0) {
                totalRisk += risk;
                explanation.append(rule.getExplanation()).append("; ");
            }
        }

        Decision decision = new Decision();
        decision.setRiskScore(totalRisk);
        decision.setExplanation(explanation.toString());

        if (totalRisk >= 80) {
            decision.setDecisionType(DecisionType.BLOCK);
        } else if (totalRisk >= 40) {
            decision.setDecisionType(DecisionType.DELAY);
        } else {
            decision.setDecisionType(DecisionType.ALLOW);
        }

        return decision;
    }
}