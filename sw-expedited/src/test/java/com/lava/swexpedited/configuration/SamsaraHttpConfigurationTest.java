package com.lava.swexpedited.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.boot.autoconfigure.app.SamsaraProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SamsaraHttpConfigurationTest {

    private final SamsaraHttpConfiguration samsaraHttpConfiguration = new SamsaraHttpConfiguration();

    @Test
    void samsaraRestClient_buildsNonNullClient() {
        SamsaraProperties samsaraProperties = new SamsaraProperties("test-token", "https://api.samsara.com");

        RestClient restClient =
                this.samsaraHttpConfiguration.samsaraRestClient(RestClient.builder(), samsaraProperties);

        assertThat(restClient).isNotNull();
    }
}
