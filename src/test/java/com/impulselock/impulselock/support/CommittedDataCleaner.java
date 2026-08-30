package com.impulselock.impulselock.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deletes everything a test committed for real, for the integration tests that deliberately are
 * NOT {@code @Transactional} and therefore get no rollback (today just
 * {@code SessionBoundaryIntegrationTest} - see its class javadoc for why it must stay that way).
 *
 * <p><b>Why this is needed.</b> {@link AbstractIntegrationTest} hands every test class one static
 * MySQL container for the whole JVM run, so rows a non-transactional class commits are still there
 * when the next class runs. That is exactly how
 * {@code AdminControllerIntegrationTest.adminCanListAndUpdateUserStatus} came to see 6 users
 * instead of its own 2: four leaked in from the four non-transactional session-boundary tests, and
 * whether that assertion passed came down to class execution order.
 *
 * <p>Only per-test data is cleared. The Flyway-seeded reference tables ({@code roles},
 * {@code rule_configs}, {@code decision_thresholds}) are left alone - tests read those, and the one
 * test that edits a rule config is {@code @Transactional} and rolls its change back.
 */
public final class CommittedDataCleaner {

    /**
     * Child-first. {@code transactions.user_id} is the one FK to {@code users} declared without
     * {@code ON DELETE CASCADE} (see {@code V1__init_schema.sql}), so it genuinely has to go first
     * or the {@code users} delete fails; the rest would cascade, but deleting them explicitly keeps
     * the intent readable rather than resting on schema details staying put.
     */
    private static final String[] TABLES_IN_DELETE_ORDER = {
            "transactions",
            "audit_log",
            "refresh_tokens",
            "restricted_categories",
            "user_roles",
            "users"
    };

    private CommittedDataCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }
}
