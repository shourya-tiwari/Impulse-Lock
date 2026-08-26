package com.impulselock.impulselock.dto;

public class TopTriggeredRuleResponse {

    private final String ruleCode;
    private final long triggerCount;

    public TopTriggeredRuleResponse(String ruleCode, long triggerCount) {
        this.ruleCode = ruleCode;
        this.triggerCount = triggerCount;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public long getTriggerCount() {
        return triggerCount;
    }
}
