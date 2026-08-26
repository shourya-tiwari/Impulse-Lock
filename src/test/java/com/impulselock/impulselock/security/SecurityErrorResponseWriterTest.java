package com.impulselock.impulselock.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.impulselock.impulselock.dto.ErrorResponse;
import com.impulselock.impulselock.logging.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(objectMapper);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesTheSameErrorResponseShapeGlobalExceptionHandlerUses() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required", "/api/v2/transactions/history");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getError()).isEqualTo("Unauthorized");
        assertThat(body.getMessage()).isEqualTo("Authentication is required");
        assertThat(body.getPath()).isEqualTo("/api/v2/transactions/history");
        assertThat(body.getCorrelationId()).isEqualTo("test-correlation-id");
    }
}
