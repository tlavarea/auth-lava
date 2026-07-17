package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API key and endpoint for Google's Routes API ({@code computeRouteMatrix}), used by {@code RouteMatrixClient} to
 * compute driving distance between a driver's manifest destination and a shipment's pickup location. apiKey
 * deliberately has no {@code @NotBlank} - same reasoning as {@code VektorProperties.username()}: validating it eagerly
 * would fail the whole application context for any developer running sw-expedited locally without a real key, not just
 * the pickup-match step. A blank key instead flows through to a real (rejected) request the first time
 * {@code pickupMatchStep} actually runs.
 */
@ConfigurationProperties(prefix = "google-maps")
@Validated
public record GoogleMapsProperties(String apiKey, @NotBlank String baseUrl, Duration retryBackoff) {}
