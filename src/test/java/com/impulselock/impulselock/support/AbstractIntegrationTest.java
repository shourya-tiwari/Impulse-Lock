package com.impulselock.impulselock.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Shared base for tests that need a real MySQL with the actual Flyway-migrated schema applied
 * (see docs/v2/testing-strategy.md#repository-tests). The container is a static singleton reused
 * across every test class that extends this within a single JVM run; Testcontainers' Ryuk
 * resource reaper cleans it up after the JVM exits, so it is deliberately never stopped manually.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8.4");
}
