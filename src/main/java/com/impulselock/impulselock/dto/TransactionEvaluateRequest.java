package com.impulselock.impulselock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request shape for {@code POST /api/v2/transactions/evaluate}. No user identifier field - the acting
 * user is always the authenticated caller, resolved from the JWT (see
 * docs/v2/security-design.md and docs/v2/api-design.md#conventions). Closes
 * docs/v1/design-decisions.md item 7 (client-supplied userId with no ownership check).
 */
public class TransactionEvaluateRequest {

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal amount;

    @Size(max = 50)
    private String category;

    @Size(max = 100)
    private String merchant;

    private LocalDateTime occurredAt;

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
