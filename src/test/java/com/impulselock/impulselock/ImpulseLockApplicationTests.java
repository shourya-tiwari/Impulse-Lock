package com.impulselock.impulselock;

import com.impulselock.impulselock.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Extends AbstractIntegrationTest so this smoke test runs against a real Testcontainers MySQL
// with the Flyway-migrated schema, rather than requiring a local MySQL at the datasource URL
// hardcoded in application.properties (see docs/v2/testing-strategy.md).
@SpringBootTest
class ImpulseLockApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
