package com.impulselock.impulselock.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Generates or propagates a request correlation ID, ties every log line for a request together
 * via MDC, and echoes it back as a response header - see docs/v2/architecture.md#logging. Ties
 * into {@code AuditLogService.record}, which reads the same MDC key so an audit_log row can be
 * correlated back to the request's log lines.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} makes Spring Boot's generic filter auto-registration run
 * this before every other filter, including Spring Security's own chain - so even a 401/403
 * response carries a correlation ID. Unlike {@code JwtAuthenticationFilter}, this filter is
 * never also manually added to a {@code SecurityFilterChain}, so there's no double-registration
 * risk here (see {@code FilterRegistrationConfig} for where that risk *does* apply and is fixed).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
