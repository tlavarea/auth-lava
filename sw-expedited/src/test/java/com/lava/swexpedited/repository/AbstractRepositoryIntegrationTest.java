package com.lava.swexpedited.repository;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = RepositoryTestConfiguration.class)
@ActiveProfiles("test")
abstract class AbstractRepositoryIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = PostgresTestContainer.INSTANCE;
}
