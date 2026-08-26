package com.impulselock.impulselock.service;

import com.impulselock.impulselock.exception.DatabaseOperationException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Centralizes the "let DataIntegrityViolationException through as-is (GlobalExceptionHandler
 * maps it to 409), wrap every other DataAccessException as DatabaseOperationException (mapped
 * to 500)" policy that was previously duplicated ad hoc across service methods - and, per
 * docs/v1/error-handling.md#notable-gaps, applied inconsistently (e.g. V1's UserRepository never
 * wrapped failures at all). Phase 4 audits every DB-touching service method for this same
 * pattern (see docs/v2/tasks.md, Phase 4) - centralizing it here means "consistent" is
 * structural, not just a convention to remember.
 */
public final class DatabaseOperations {

    private DatabaseOperations() {
    }

    public static <T> T execute(Supplier<T> operation, String failureMessage) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new DatabaseOperationException(failureMessage, exception);
        }
    }
}
