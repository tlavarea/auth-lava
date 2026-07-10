package com.lava.service;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

final class MailpitTestContainer {

    static final int SMTP_PORT = 1025;
    static final int HTTP_PORT = 8025;

    static final GenericContainer<?> INSTANCE = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(SMTP_PORT, HTTP_PORT);

    static {
        INSTANCE.start();
    }

    private MailpitTestContainer() {}
}
