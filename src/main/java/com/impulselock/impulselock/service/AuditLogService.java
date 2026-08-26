package com.impulselock.impulselock.service;

import com.impulselock.impulselock.entity.AuditLog;
import com.impulselock.impulselock.repository.AuditLogRepository;
import com.impulselock.impulselock.repository.AuditLogSpecifications;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only for now - nothing writes to {@code audit_log} yet (see {@link AuditLog}'s class
 * javadoc). The write side (an AOP {@code @Auditable} aspect) arrives in Phase 4; this service
 * only backs {@code GET /api/v2/admin/audit-logs}.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(AuditLogSpecifications.byAction(action))
                .and(AuditLogSpecifications.createdBetween(from, to));
        return auditLogRepository.findAll(spec, pageable);
    }
}
