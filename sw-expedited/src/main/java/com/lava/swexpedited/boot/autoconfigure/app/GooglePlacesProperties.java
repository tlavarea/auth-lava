package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Endpoint for Google's Places API (New) Text Search, used by {@code GooglePlacesClient} to normalize an address
 * Google's Routes API can't geocode to a drivable point (see that class's javadoc) into its place's canonical display
 * name. No separate API key here - {@code GooglePlacesClient} reuses {@link GoogleMapsProperties#apiKey()}, since
 * Places API (New) is enabled on the same Google Cloud project/key already used for Routes/Matrix.
 */
@ConfigurationProperties(prefix = "google-places")
@Validated
public record GooglePlacesProperties(@NotBlank String baseUrl, Duration retryBackoff) {}
