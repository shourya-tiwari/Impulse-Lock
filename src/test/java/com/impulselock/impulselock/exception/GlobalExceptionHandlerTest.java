package com.impulselock.impulselock.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.impulselock.impulselock.dto.ErrorResponse;
import com.impulselock.impulselock.logging.CorrelationIdFilter;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Unit tests (no Spring context, no Docker) for Phase 4's expanded exception matrix - see
 * docs/v2/api-design.md#error-format and docs/v2/tasks.md, Phase 4. Deliberately runnable in
 * this environment, unlike most of the project's controller/repository tests which need
 * Testcontainers.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = requestFor("/api/v2/test");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void userNotFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserNotFound(new UserNotFoundException("no such user"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("no such user");
    }

    @Test
    void transactionNotFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTransactionNotFound(new TransactionNotFoundException("abc-123"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void userAlreadyExistsMapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserAlreadyExists(new UserAlreadyExistsException("taken"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidRefreshTokenMapsTo401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("bad token"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticationExceptionMapsTo401WithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAuthenticationException(new BadCredentialsException("wrong password for bob"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Deliberately generic - must not leak which case applied (see the handler's javadoc).
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void accessDeniedMapsTo403UsingTheExceptionsOwnMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Only admins may view another user's dashboard"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Only admins may view another user's dashboard");
    }

    @Test
    void jwtExceptionMapsTo401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleJwtException(new JwtException("malformed"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dataIntegrityViolationMapsTo409WithoutLeakingTheRawDbMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("Duplicate entry 'bob' for key 'uk_users_username'"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).doesNotContain("uk_users_username");
    }

    @Test
    void databaseOperationExceptionMapsTo500() {
        ResponseEntity<ErrorResponse> response = handler.handleDatabaseError(
                new DatabaseOperationException("Failed to save user in database", new RuntimeException("cause")), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new IllegalArgumentException("amount is required"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("amount is required");
    }

    @Test
    void genericExceptionMapsTo500WithoutLeakingItsMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("some internal detail nobody should see"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Unexpected server error");
    }

    @Test
    void validationFailureMapsTo400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "must not be null"));
        MethodParameter methodParameter =
                new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationFailure(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getFieldErrors()).hasSize(1);
        assertThat(response.getBody().getFieldErrors().get(0).getField()).isEqualTo("amount");
        assertThat(response.getBody().getFieldErrors().get(0).getMessage()).isEqualTo("must not be null");
    }

    @Test
    void constraintViolationMapsTo400WithFieldErrors() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ValidatableFixture>> violations =
                validator.validate(new ValidatableFixture(""));

        ResponseEntity<ErrorResponse> response =
                handler.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(violations), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getFieldErrors()).hasSize(1);
    }

    @Test
    void errorResponseCarriesTheCurrentCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-correlation-id");

        ResponseEntity<ErrorResponse> response =
                handler.handleUserNotFound(new UserNotFoundException("no such user"), request);

        assertThat(response.getBody().getCorrelationId()).isEqualTo("test-correlation-id");
    }

    private static MockHttpServletRequest requestFor(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
    }

    private static final class ValidatableFixture {
        @NotBlank
        private final String value;

        private ValidatableFixture(String value) {
            this.value = value;
        }
    }
}
