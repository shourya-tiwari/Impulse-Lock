package com.impulselock.impulselock.rules;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;

public abstract class AbstractSpendingRule implements SpendingRule {

    protected double riskWeight;
    protected String explanation;

    public AbstractSpendingRule(double riskWeight, String explanation) {
        this.riskWeight = riskWeight;
        this.explanation = explanation;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }

    protected double getRiskWeight() {
        return riskWeight;
    }

    public abstract double evaluate(Transaction transaction, User userProfile);
}
