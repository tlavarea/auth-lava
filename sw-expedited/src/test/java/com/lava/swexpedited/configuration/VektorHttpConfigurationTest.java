package com.lava.swexpedited.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VektorHttpConfigurationTest {

    private final VektorHttpConfiguration vektorHttpConfiguration = new VektorHttpConfiguration();

    @Test
    void vektorRestClient_buildsNonNullClient() {
        VektorProperties vektorProperties = new VektorProperties(
                "user@example.com",
                "hunter2",
                "test-company-id",
                "https://app.vektortms.com",
                Duration.ofSeconds(5),
                List.of("manifest_in_progress"),
                14,
                60);

        RestClient restClient = this.vektorHttpConfiguration.vektorRestClient(RestClient.builder(), vektorProperties);

        assertThat(restClient).isNotNull();
    }
}
