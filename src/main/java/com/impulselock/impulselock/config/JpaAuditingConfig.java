package com.impulselock.impulselock.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate}/{@code @LastModifiedDate} on entities (see entity package) -
 * closes the "no audit columns" gap noted in docs/v1/database.md#notable-absences.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
