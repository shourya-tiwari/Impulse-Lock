package com.impulselock.impulselock.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion (and, optionally, failure) should produce
 * an {@code audit_log} row - see docs/v2/architecture.md#audit-logging. Handled by
 * {@link AuditAspect}. Deliberately method-level only, not annotation-driven for entityId/
 * metadata extraction - see {@code AuditAspect}'s javadoc for why that stays a small, explicit
 * dispatch rather than generic reflection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {

    /** Action recorded when the method returns normally, e.g. {@code "TRANSACTION_EVALUATED"}. */
    String action();

    /** e.g. {@code "USER"}, {@code "TRANSACTION"}, {@code "RULE_CONFIG"} - stored as-is on the audit row. */
    String entityType() default "";

    /**
     * Action recorded when the method throws instead of returning - e.g. {@code "LOGIN_FAILURE"}
     * for {@code AuthService.login}. Left blank (the default) means failures for this method
     * simply aren't audited - most annotated methods only have a meaningful "it happened"
     * event, not a security-relevant failure mode worth its own audit entry.
     */
    String failureAction() default "";
}
