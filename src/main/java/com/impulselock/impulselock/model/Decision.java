package com.impulselock.impulselock.model;

import java.util.List;

public class Decision {

    private DecisionType decisionType;
    private double riskScore;
    private String explanation;
    private List<TriggeredRuleEntry> triggeredRules;

    public DecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(DecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<TriggeredRuleEntry> getTriggeredRules() {
        return triggeredRules;
    }

    public void setTriggeredRules(List<TriggeredRuleEntry> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }
}
