package com.impulselock.impulselock.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Bean Validation constraints added in Phase 4 directly against a real
 * {@link Validator} - no Spring context, no Docker, runnable in any environment (see
 * docs/v2/testing-strategy.md#unit-tests-no-spring-context-fastest-tier).
 */
class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void registerRequestAcceptsValidInput() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void registerRequestRejectsBlankUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setEmail("alice@example.com");
        request.setPassword("password123");

        assertThat(propertyPaths(validator.validate(request))).contains("username");
    }

    @Test
    void registerRequestRejectsInvalidEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("not-an-email");
        request.setPassword("password123");

        assertThat(propertyPaths(validator.validate(request))).contains("email");
    }

    @Test
    void registerRequestRejectsPasswordShorterThanEightCharacters() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("short");

        assertThat(propertyPaths(validator.validate(request))).contains("password");
    }

    @Test
    void loginRequestRejectsBlankFields() {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");

        assertThat(propertyPaths(validator.validate(request))).contains("username", "password");
    }

    @Test
    void userPreferencesUpdateRequestRejectsSensitivityLevelAboveTen() {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest();
        request.setDailyLimit(BigDecimal.valueOf(1000));
        request.setSensitivityLevel(11);

        assertThat(propertyPaths(validator.validate(request))).contains("sensitivityLevel");
    }

    @Test
    void userPreferencesUpdateRequestRejectsSensitivityLevelBelowOne() {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest();
        request.setDailyLimit(BigDecimal.valueOf(1000));
        request.setSensitivityLevel(0);

        assertThat(propertyPaths(validator.validate(request))).contains("sensitivityLevel");
    }

    @Test
    void userPreferencesUpdateRequestRejectsMissingDailyLimit() {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest();
        request.setSensitivityLevel(5);

        assertThat(propertyPaths(validator.validate(request))).contains("dailyLimit");
    }

    @Test
    void userPreferencesUpdateRequestAcceptsValidInput() {
        UserPreferencesUpdateRequest request = new UserPreferencesUpdateRequest();
        request.setDailyLimit(BigDecimal.valueOf(1000));
        request.setSensitivityLevel(5);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void transactionEvaluateRequestRejectsNegativeAmount() {
        TransactionEvaluateRequest request = new TransactionEvaluateRequest();
        request.setAmount(BigDecimal.valueOf(-1));

        assertThat(propertyPaths(validator.validate(request))).contains("amount");
    }

    @Test
    void transactionEvaluateRequestRejectsMissingAmount() {
        TransactionEvaluateRequest request = new TransactionEvaluateRequest();

        assertThat(propertyPaths(validator.validate(request))).contains("amount");
    }

    @Test
    void restrictedCategoryRequestRejectsBlankCategory() {
        RestrictedCategoryRequest request = new RestrictedCategoryRequest();
        request.setCategory(" ");

        assertThat(propertyPaths(validator.validate(request))).contains("category");
    }

    @Test
    void ruleConfigUpdateRequestRejectsNegativeWeight() {
        RuleConfigUpdateRequest request = new RuleConfigUpdateRequest();
        request.setWeight(BigDecimal.valueOf(-5));

        assertThat(propertyPaths(validator.validate(request))).contains("weight");
    }

    private <T> Set<String> propertyPaths(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
