package com.impulselock.impulselock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's JacksonAutoConfiguration centers on Jackson 3's JsonMapper, not the classic
 * com.fasterxml.jackson.databind.ObjectMapper - it no longer auto-configures a general-purpose
 * bean of that legacy type. SecurityErrorResponseWriter needs one (it writes JSON outside the
 * MVC pipeline, from a Spring Security entry point/handler), so it's provided explicitly here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
