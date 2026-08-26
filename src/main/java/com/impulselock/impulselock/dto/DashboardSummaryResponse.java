package com.impulselock.impulselock.dto;

import java.math.BigDecimal;

public class DashboardSummaryResponse {

    private final long transactionCount;
    private final BigDecimal totalSpend;
    private final long allowCount;
    private final long delayCount;
    private final long blockCount;
    private final double averageRiskScore;

    public DashboardSummaryResponse(long transactionCount, BigDecimal totalSpend, long allowCount,
                                     long delayCount, long blockCount, double averageRiskScore) {
        this.transactionCount = transactionCount;
        this.totalSpend = totalSpend;
        this.allowCount = allowCount;
        this.delayCount = delayCount;
        this.blockCount = blockCount;
        this.averageRiskScore = averageRiskScore;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public BigDecimal getTotalSpend() {
        return totalSpend;
    }

    public long getAllowCount() {
        return allowCount;
    }

    public long getDelayCount() {
        return delayCount;
    }

    public long getBlockCount() {
        return blockCount;
    }

    public double getAverageRiskScore() {
        return averageRiskScore;
    }
}
