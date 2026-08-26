package com.impulselock.impulselock.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Shared base for tests that need a real MySQL with the actual Flyway-migrated schema applied
 * (see docs/v2/testing-strategy.md#repository-tests). The container is a static singleton reused
 * across every test class that extends this within a single JVM run - deliberately NOT managed via
 * JUnit 5's {@code @Testcontainers}/{@code @Container} (that lifecycle is per-test-class: started
 * in beforeAll and stopped in afterAll of *each* class, even for an inherited static field, so
 * every one of the ~10 classes extending this was paying its own ~60-90s MySQL container startup
 * cost). This is Testcontainers' documented "singleton container" pattern instead: the static
 * initializer runs once, the first time any subclass is loaded, and JUnit never stops it -
 * Testcontainers' Ryuk resource reaper cleans it up when the JVM exits.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8.4");

    static {
        MYSQL_CONTAINER.start();
    }
}
