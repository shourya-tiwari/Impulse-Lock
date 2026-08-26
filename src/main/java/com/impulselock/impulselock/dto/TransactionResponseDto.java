package com.impulselock.impulselock.dto;

import com.impulselock.impulselock.model.DecisionType;
import com.impulselock.impulselock.model.TriggeredRuleEntry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shape for {@code POST /transactions/evaluate}, {@code GET /transactions/{publicId}},
 * and each row of {@code GET /transactions/history} - see docs/v2/api-design.md. Built via
 * {@code TransactionMapper} straight from the persisted {@code Transaction} entity (Phase 2
 * already populates decisionType/riskScore/explanation/triggeredRules on save, so the same
 * entity serves all three call sites without a separate write-time response object).
 */
public class TransactionResponseDto {

    private String publicId;
    private BigDecimal amount;
    private String category;
    private String merchant;
    private LocalDateTime occurredAt;
    private DecisionType decisionType;
    private BigDecimal riskScore;
    private String explanation;
    private List<TriggeredRuleEntry> triggeredRules;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public DecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(DecisionType decisionType) {
        this.decisionType = decisionType;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
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
