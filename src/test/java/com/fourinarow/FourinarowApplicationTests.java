package com.fourinarow;

import org.junit.jupiter.api.Test;

class FourinarowApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
		// Passes if the application starts: all beans are created,
		// Flyway migrations run on a fresh database, and Hibernate validates the schema.
	}
}