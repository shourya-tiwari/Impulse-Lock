package com.impulselock.impulselock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Defined as its own bean (rather than inline in SecurityConfig) since it was introduced back
 * in Phase 0 - before the filter chain existed - so passwords were hashed correctly from the
 * very first user ever created. Also consumed by Spring Security's auto-configured
 * DaoAuthenticationProvider (see SecurityConfig): with exactly one PasswordEncoder and one
 * UserDetailsService bean in context, Spring wires them together with no extra config.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
