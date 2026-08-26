package com.impulselock.impulselock.config;

import com.impulselock.impulselock.security.JwtAuthenticationFilter;
import com.impulselock.impulselock.security.RestAccessDeniedHandler;
import com.impulselock.impulselock.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Stateless JWT-based security (see docs/v2/security-design.md). CSRF is disabled because there
 * is no cookie-based session driving state changes - the one cookie in the system (the refresh
 * token) is httpOnly/SameSite=Strict, scoped to the /api/v2/auth path, and only ever read by
 * /auth/refresh and /auth/logout: a deliberate narrow exception, not an oversight.
 *
 * <p>Deviation from docs/v2/tasks.md's literal Phase 1 wording: the {@code /api/v2/admin/**}
 * rule is added now even though no admin controller exists until Phase 3, purely so RBAC's
 * 403 path is exercisable and testable in Phase 1 (see AuthIntegrationTest) - it protects a URL
 * pattern, not a concrete endpoint.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     CorsConfigurationSource corsConfigurationSource,
                                                     JwtAuthenticationFilter jwtAuthenticationFilter,
                                                     RestAuthenticationEntryPoint authenticationEntryPoint,
                                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v2/auth/**").permitAll()
                        .requestMatchers("/api/v2/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
