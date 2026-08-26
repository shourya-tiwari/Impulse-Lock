package com.impulselock.impulselock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.dto.ErrorResponse;
import com.impulselock.impulselock.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Shared by {@link RestAuthenticationEntryPoint} and {@link RestAccessDeniedHandler} so 401/403
 * responses use the same JSON {@code ErrorResponse} shape as {@code GlobalExceptionHandler} (see
 * docs/v2/api-design.md#error-format) instead of Spring Security's default HTML/redirect.
 */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        ErrorResponse body = new ErrorResponse();
        body.setTimestamp(LocalDateTime.now());
        body.setStatus(status.value());
        body.setError(status.getReasonPhrase());
        body.setMessage(message);
        body.setPath(path);
        body.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
