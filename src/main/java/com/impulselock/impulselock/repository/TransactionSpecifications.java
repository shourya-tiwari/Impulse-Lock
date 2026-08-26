package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic filter builders for the advanced transaction-history endpoint (wired up to
 * {@code GET /api/v2/transactions/history} in Phase 3 - see docs/v2/api-design.md). Not yet
 * called from any controller in Phase 0; exists as persistence-layer infrastructure.
 *
 * <p>A decision-type filter is intentionally not included yet: {@code Transaction.decisionType}
 * isn't mapped on the entity until Phase 2 persists the computed Decision onto the row (see
 * {@link Transaction}'s class-level note and docs/v2/tasks.md, Phase 2). Add {@code byDecisionType}
 * here once that field exists.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> byUser(User user) {
        return (root, query, cb) -> cb.equal(root.get("user"), user);
    }

    public static Specification<Transaction> occurredBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("occurredAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
            }
            if (to != null) {
                return cb.lessThanOrEqualTo(root.get("occurredAt"), to);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Transaction> byCategory(String category) {
        return (root, query, cb) -> category == null
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    public static Specification<Transaction> byMerchant(String merchant) {
        return (root, query, cb) -> merchant == null
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("merchant")), merchant.toLowerCase());
    }

    public static Specification<Transaction> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("amount"), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("amount"), min);
            }
            if (max != null) {
                return cb.lessThanOrEqualTo(root.get("amount"), max);
            }
            return cb.conjunction();
        };
    }
}
