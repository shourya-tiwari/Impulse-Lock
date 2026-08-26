package com.impulselock.impulselock.repository;

import com.impulselock.impulselock.entity.AuditLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.Specification;

/** Filter builders for {@code GET /api/v2/admin/audit-logs} (see docs/v2/api-design.md). */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> byAction(String action) {
        return (root, query, cb) -> action == null ? cb.conjunction() : cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> createdBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            }
            if (to != null) {
                return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            }
            return cb.conjunction();
        };
    }
}
