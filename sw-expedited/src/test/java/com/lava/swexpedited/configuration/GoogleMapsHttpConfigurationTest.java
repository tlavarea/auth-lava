package com.lava.swexpedited.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.boot.autoconfigure.app.GoogleMapsProperties;
import com.lava.swexpedited.boot.autoconfigure.app.GooglePlacesProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GoogleMapsHttpConfigurationTest {

    private final GoogleMapsHttpConfiguration googleMapsHttpConfiguration = new GoogleMapsHttpConfiguration();

    @Test
    void googleMapsRestClient_buildsNonNullClient() {
        GoogleMapsProperties googleMapsProperties =
                new GoogleMapsProperties("test-api-key", "https://routes.googleapis.com", Duration.ofSeconds(5));

        RestClient restClient =
                this.googleMapsHttpConfiguration.googleMapsRestClient(RestClient.builder(), googleMapsProperties);

        assertThat(restClient).isNotNull();
    }

    @Test
    void googleRoutesComputeRestClient_buildsNonNullClient() {
        GoogleMapsProperties googleMapsProperties =
                new GoogleMapsProperties("test-api-key", "https://routes.googleapis.com", Duration.ofSeconds(5));

        RestClient restClient = this.googleMapsHttpConfiguration.googleRoutesComputeRestClient(
                RestClient.builder(), googleMapsProperties);

        assertThat(restClient).isNotNull();
    }

    @Test
    void googlePlacesRestClient_buildsNonNullClient() {
        GoogleMapsProperties googleMapsProperties =
                new GoogleMapsProperties("test-api-key", "https://routes.googleapis.com", Duration.ofSeconds(5));
        GooglePlacesProperties googlePlacesProperties =
                new GooglePlacesProperties("https://places.googleapis.com", Duration.ofSeconds(5));

        RestClient restClient = this.googleMapsHttpConfiguration.googlePlacesRestClient(
                RestClient.builder(), googleMapsProperties, googlePlacesProperties);

        assertThat(restClient).isNotNull();
    }
}
