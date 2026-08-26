package com.impulselock.impulselock.service;

import com.impulselock.impulselock.entity.AuditLog;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.logging.CorrelationIdFilter;
import com.impulselock.impulselock.repository.AuditLogRepository;
import com.impulselock.impulselock.repository.AuditLogSpecifications;
import com.impulselock.impulselock.repository.UserRepository;
import com.impulselock.impulselock.security.SecurityUser;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link #search} backs {@code GET /api/v2/admin/audit-logs} (built in Phase 3, when this table
 * had no writer yet). {@link #record} is the writer, added in Phase 4 - called by
 * {@code AuditAspect} for every {@code @Auditable} method and directly by a couple of call sites
 * that need conditional logic AOP doesn't fit (see {@code AuditAspect}'s javadoc).
 *
 * <p>{@code record} runs in its own transaction ({@code REQUIRES_NEW}, suspending whatever
 * transaction the caller is in) and never lets an exception escape - audit logging is
 * deliberately best-effort: a failure to write an audit row must never fail or roll back the
 * business operation it's describing (see docs/v2/architecture.md#audit-logging).
 *
 * <p><b>Deviation</b> from docs/v2/tasks.md's literal "separate transaction/thread" wording:
 * this stays on the calling thread (only the transaction is separate/suspended-and-resumed).
 * Actually dispatching to a background thread would need a thread pool, cross-thread MDC
 * propagation, and its own failure handling - real complexity for marginal benefit at this
 * project's scale - so it's deliberately not done.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(AuditLogSpecifications.byAction(action))
                .and(AuditLogSpecifications.createdBetween(from, to));
        return auditLogRepository.findAll(spec, pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, Map<String, Object> metadata) {
        try {
            User actor = currentActor();
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            String ipAddress = currentRequestIp();

            auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, metadata, ipAddress, correlationId));
        } catch (Exception exception) {
            log.warn("Failed to write audit log entry for action={} entityType={} entityId={}",
                    action, entityType, entityId, exception);
        }
    }

    private User currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            return null;
        }
        return userRepository.findByUsername(securityUser.getUsername()).orElse(null);
    }

    private String currentRequestIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getRemoteAddr();
    }
}
