package com.lava.repository;

import org.testcontainers.containers.PostgreSQLContainer;

final class PostgresTestContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresTestContainer() {}
}
