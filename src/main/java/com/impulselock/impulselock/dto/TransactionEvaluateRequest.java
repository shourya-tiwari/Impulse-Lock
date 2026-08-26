package com.impulselock.impulselock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transitional request shape for {@code POST /transaction/evaluate} while Phase 1 auth doesn't
 * exist yet - {@code username} stands in for the not-yet-authenticated caller identity (Phase 1
 * removes this field entirely and resolves the acting user from the JWT instead; see
 * docs/v2/security-design.md and docs/v2/api-design.md#conventions).
 */
public class TransactionEvaluateRequest {

    private String username;
    private BigDecimal amount;
    private String category;
    private String merchant;
    private LocalDateTime occurredAt;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
}
