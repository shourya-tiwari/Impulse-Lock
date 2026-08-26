package com.impulselock.impulselock.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serves the live OpenAPI 3 spec at {@code /v3/api-docs} and an interactive explorer at
 * {@code /swagger-ui.html} (see docs/v2/api-design.md, "OpenAPI / Swagger"). {@code bearerAuth}
 * is applied globally so "Authorize" in the UI attaches a real access token to every call;
 * {@code AuthController}'s public endpoints override it back off individually (see
 * {@code @SecurityRequirements} there) since they don't need - and for {@code /refresh}/
 * {@code /logout}, don't use - a bearer token at all.
 *
 * <p>Enabled in the default/docker profile; disabled entirely in {@code prod} (see
 * application.properties' {@code prod} block and docs/v2/deployment-plan.md) rather than
 * ROLE_ADMIN-gated - gating would need Swagger UI's own static assets to load unauthenticated
 * anyway, which defeats the point of restricting it.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI impulseLockOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ImpulseLock API")
                        .version("v2")
                        .description("Rule-based transaction risk evaluation API. "
                                + "See docs/v2/api-design.md for the full design."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
