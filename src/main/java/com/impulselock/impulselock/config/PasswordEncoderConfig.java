package com.impulselock.impulselock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing only (spring-security-crypto) - no filter chain, no auto-configured
 * endpoint security yet. That full Spring Security setup is Phase 1 (see docs/v2/security-design.md).
 * Defined now so passwords are hashed correctly from the very first user ever created,
 * rather than retrofitted once Phase 1 lands.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
