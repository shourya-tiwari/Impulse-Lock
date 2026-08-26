package com.impulselock.impulselock.audit;

import com.impulselock.impulselock.entity.RuleConfig;
import com.impulselock.impulselock.entity.Transaction;
import com.impulselock.impulselock.entity.User;
import com.impulselock.impulselock.service.AuditLogService;
import java.util.Map;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Calls {@link AuditLogService#record} after a {@code @Auditable}-annotated method returns
 * (success) or throws (only if {@code failureAction} is set). The actual "who/where" context
 * (actor from SecurityContext, correlation ID from MDC, IP from the current request) is resolved
 * inside {@code AuditLogService.record} itself, not here - that keeps this aspect's only job
 * "decide when to audit and what entity/action it's about," reusable identically whether the
 * call came from AOP or a direct manual call (see {@code DashboardService}'s admin cross-user
 * view, which calls {@code AuditLogService.record} directly instead - a conditional-only-when-
 * admin-overrides case that doesn't fit "always audit every call to this method").
 *
 * <p>{@code extractEntityId} is a small, finite dispatch over the handful of entity types this
 * project actually audits (User/Transaction/RuleConfig) rather than generic reflection - there
 * are only a few call sites, and a reflective "find something that looks like an ID" mechanism
 * would be harder to read for no real benefit at this scale.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditSuccess(Auditable auditable, Object result) {
        auditLogService.record(auditable.action(), blankToNull(auditable.entityType()), extractEntityId(result), null);
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "exception")
    public void auditFailure(Auditable auditable, Exception exception) {
        if (auditable.failureAction().isBlank()) {
            return;
        }
        Map<String, Object> metadata = Map.of("error", exception.getClass().getSimpleName());
        auditLogService.record(auditable.failureAction(), blankToNull(auditable.entityType()), null, metadata);
    }

    private String extractEntityId(Object result) {
        if (result instanceof User user) {
            return user.getUsername();
        }
        if (result instanceof Transaction transaction) {
            return transaction.getPublicId();
        }
        if (result instanceof RuleConfig ruleConfig) {
            return ruleConfig.getRuleCode();
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
