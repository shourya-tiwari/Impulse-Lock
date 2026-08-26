package com.impulselock.impulselock.config;

import com.impulselock.impulselock.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code JwtAuthenticationFilter} is a {@code @Component} (so {@code SecurityConfig} can inject
 * it into the Spring Security filter chain via {@code addFilterBefore(...)}) - but that also
 * makes Spring Boot's generic {@code Filter}-bean auto-registration pick it up as a second,
 * independent top-level servlet filter, running it twice per request. Harmless here in practice
 * (the filter is idempotent - it no-ops once {@code SecurityContextHolder} already has an
 * authentication), but wasteful and not the intended behavior. This disables that redundant
 * generic registration; the filter still runs exactly once, via {@code SecurityConfig}'s
 * explicit wiring. Identified while adding {@code CorrelationIdFilter} in Phase 4, which needed
 * careful thought about filter registration/ordering anyway (see docs/v2/tasks.md, Phase 4).
 */
@Configuration
public class FilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
