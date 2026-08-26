package com.impulselock.impulselock.exception;

import com.impulselock.impulselock.dto.ErrorResponse;
import com.impulselock.impulselock.dto.FieldErrorDto;
import com.impulselock.impulselock.logging.CorrelationIdFilter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception,
                                                            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Also thrown deliberately for "exists but belongs to someone else" (see
     * TransactionService.getByPublicId) - a non-owner gets the same 404 an actually-missing
     * publicId would, so the response never confirms another user's transaction exists (see
     * docs/v2/api-design.md#error-format).
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(TransactionNotFoundException exception,
                                                                    HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException exception,
                                                                  HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException exception,
                                                                    HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request.getRequestURI());
    }

    /** Thrown by {@link com.impulselock.impulselock.security.LoginRateLimiter}. */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyLoginAttempts(TooManyLoginAttemptsException exception,
                                                                     HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Covers login failures (bad password, disabled account, etc). Thrown by
     * AuthenticationManager.authenticate() inside AuthService - a normal application code path,
     * not the security filter chain's own unauthenticated-request rejection (that goes through
     * RestAuthenticationEntryPoint instead; see docs/v2/security-design.md). Message is
     * deliberately generic to avoid leaking which case applied (bad username vs bad password vs
     * disabled account).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException exception,
                                                                        HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    /**
     * Reachable for AccessDeniedException thrown from within a controller/service call (e.g.
     * DashboardService's admin-only cross-user check) - URL-level authorizeHttpRequests denials
     * never reach here at all (AuthorizationFilter runs before DispatcherServlet, so those go
     * straight to RestAccessDeniedHandler via ExceptionTranslationFilter). Unlike
     * RestAccessDeniedHandler's fixed generic message, this uses the exception's own message,
     * since a manually-thrown AccessDeniedException usually has a more specific one worth
     * surfacing.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception,
                                                             HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Defensive - JwtService.parseClaims already catches JwtException internally and returns
     * Optional.empty(), so JwtAuthenticationFilter never lets one propagate this far in practice.
     * Kept in case a future JWT-touching code path doesn't swallow it the same way.
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(JwtException exception, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request.getRequestURI());
    }

    /**
     * A genuine DB constraint violation that slipped past an in-memory pre-check (e.g. a race
     * between two concurrent requests both passing a duplicate-check before either inserts).
     * Every service method that saves an entity routes DataAccessException through
     * DatabaseOperations.execute (see docs/v2/tasks.md, Phase 4), which lets this specific
     * subtype through unwrapped so it lands here instead of being folded into the generic 500
     * DatabaseOperationException produces.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception,
                                                                       HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT,
                "The request conflicts with existing data (e.g. a duplicate value)", request.getRequestURI());
    }

    @ExceptionHandler(DatabaseOperationException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseError(DatabaseOperationException exception,
                                                             HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception,
                                                                  HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDto)
                .collect(Collectors.toList());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                    HttpServletRequest request) {
        List<FieldErrorDto> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDto(violation.getPropertyPath().toString(), violation.getMessage()))
                .collect(Collectors.toList());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), fieldErrors);
    }

    /**
     * Thrown by Spring MVC itself for a URL that matches no controller mapping and no static
     * resource - without this, it fell through to the generic 500 handler below, turning a
     * simple "no such endpoint" into a spurious "Unexpected server error" instead of a 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException exception,
                                                                 HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "No such endpoint", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error",
                request.getRequestURI()
        );
    }

    private FieldErrorDto toFieldErrorDto(FieldError fieldError) {
        return new FieldErrorDto(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String path) {
        return buildErrorResponse(status, message, path, null);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, String path,
                                                              List<FieldErrorDto> fieldErrors) {
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setStatus(status.value());
        response.setError(status.getReasonPhrase());
        response.setMessage(message);
        response.setPath(path);
        response.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        response.setFieldErrors(fieldErrors);
        return ResponseEntity.status(status).body(response);
    }
}
