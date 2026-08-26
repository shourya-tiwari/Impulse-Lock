package com.impulselock.impulselock.dto;

import java.time.LocalDate;

public class RiskTrendPointResponse {

    private final LocalDate date;
    private final long transactionCount;
    private final double averageRiskScore;

    public RiskTrendPointResponse(LocalDate date, long transactionCount, double averageRiskScore) {
        this.date = date;
        this.transactionCount = transactionCount;
        this.averageRiskScore = averageRiskScore;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public double getAverageRiskScore() {
        return averageRiskScore;
    }
}
