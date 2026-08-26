package com.impulselock.impulselock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.impulselock.impulselock.exception.DatabaseOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

class DatabaseOperationsTest {

    @Test
    void returnsTheSuppliersValueOnSuccess() {
        String result = DatabaseOperations.execute(() -> "ok", "should not be used");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void letsDataIntegrityViolationExceptionThroughUnwrapped() {
        assertThatThrownBy(() -> DatabaseOperations.execute(() -> {
            throw new DataIntegrityViolationException("duplicate key");
        }, "failure message"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("duplicate key");
    }

    @Test
    void wrapsAnyOtherDataAccessExceptionAsDatabaseOperationException() {
        assertThatThrownBy(() -> DatabaseOperations.execute(() -> {
            throw new QueryTimeoutException("connection pool exhausted");
        }, "Failed to save user in database"))
                .isInstanceOf(DatabaseOperationException.class)
                .hasMessage("Failed to save user in database");
    }
}
