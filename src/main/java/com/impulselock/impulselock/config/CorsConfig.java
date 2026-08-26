package com.impulselock.impulselock.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Evolves V1's hardcoded-to-localhost:3000 CORS config (see docs/v1/backend.md#corsconfig) to
 * read allowed origins from configuration (app.cors.allowed-origins), so a deployed frontend
 * origin can be added per environment without a code change (see docs/v2/security-design.md).
 * Exposed as a {@link CorsConfigurationSource} bean rather than {@code WebMvcConfigurer} because
 * the Spring Security filter chain (see {@link SecurityConfig}) needs to apply CORS before its
 * own authorization checks run - allowCredentials is required now that the refresh token
 * travels as a cookie.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
