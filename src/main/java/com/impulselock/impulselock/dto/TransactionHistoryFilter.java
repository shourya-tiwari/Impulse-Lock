package com.impulselock.impulselock.dto;

import com.impulselock.impulselock.model.DecisionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Parsed query-param filters for {@code GET /transactions/history} (and its CSV export). */
public record TransactionHistoryFilter(
        LocalDateTime from,
        LocalDateTime to,
        String category,
        String merchant,
        DecisionType decisionType,
        BigDecimal minAmount,
        BigDecimal maxAmount) {
}
