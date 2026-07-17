package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.GoogleMapsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * A dedicated RestClient for Google's Routes API ({@code routes.googleapis.com}). Auth is a static API key rather than
 * a per-request bearer token, so it's set as a default header here rather than attached per call site the way Vektor's
 * runtime-obtained JWT is (see {@code VektorHttpConfiguration}). The field mask is likewise fixed across every call
 * this app makes to {@code computeRouteMatrix} - {@code RouteMatrixClient} only ever needs
 * originIndex/destinationIndex/status/condition/distanceMeters/duration ({@code duration} lets
 * {@code PickupMatchTasklet} check a driver can actually reach the pickup before its window closes, not just that it's
 * within range), and Google's docs call out that {@code status} must be included or every element silently reports as
 * OK - so it's set once here too, per the same default-headers-over-repeated-per-call-headers convention. The injected
 * RestClient.Builder is used as-is: no custom request factory, per the project's established gotcha that calling
 * .requestFactory(...) on it silently breaks MockRestServiceServer-based tests.
 */
@Configuration
public class GoogleMapsHttpConfiguration {

    @Bean(name = "googleMapsRestClient")
    public RestClient googleMapsRestClient(RestClient.Builder builder, GoogleMapsProperties googleMapsProperties) {
        return builder.baseUrl(googleMapsProperties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", googleMapsProperties.apiKey())
                .defaultHeader(
                        "X-Goog-FieldMask", "originIndex,destinationIndex,status,condition,distanceMeters,duration")
                .build();
    }
}
