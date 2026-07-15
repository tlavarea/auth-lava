package com.lava.swexpedited.repository;

import org.testcontainers.postgresql.PostgreSQLContainer;

final class PostgresTestContainer {

    static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer("postgres:18-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {}
}
