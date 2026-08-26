package com.impulselock.impulselock.dto;

import java.math.BigDecimal;

public class SpendingByCategoryResponse {

    private final String category;
    private final BigDecimal totalAmount;
    private final long transactionCount;

    public SpendingByCategoryResponse(String category, BigDecimal totalAmount, long transactionCount) {
        this.category = category;
        this.totalAmount = totalAmount;
        this.transactionCount = transactionCount;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public long getTransactionCount() {
        return transactionCount;
    }
}
