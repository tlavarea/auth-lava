package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.GoogleMapsProperties;
import com.lava.swexpedited.boot.autoconfigure.app.GooglePlacesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClients for Google's Routes API ({@code routes.googleapis.com}) and Places API ({@code places.googleapis.com}).
 * Auth is a static API key rather than a per-request bearer token, so it's set as a default header here rather than
 * attached per call site the way Vektor's runtime-obtained JWT is (see {@code VektorHttpConfiguration}).
 * {@code X-Goog-FieldMask} is likewise fixed per bean rather than passed per-call, since each bean here only ever backs
 * one client hitting one API method with a fixed set of needed response fields (Google's docs require
 * {@code X-Goog-FieldMask} on every request, with no request-level way to vary it without also varying the header - so
 * one bean per distinct field mask is the natural unit here, not one bean shared across different needs). The injected
 * RestClient.Builder is used as-is on every bean: no custom request factory, per the project's established gotcha that
 * calling .requestFactory(...) on it silently breaks MockRestServiceServer-based tests. Places API reuses
 * {@link GoogleMapsProperties#apiKey()} rather than a separate key/properties class for auth - see
 * {@link GooglePlacesProperties}'s javadoc for why only its base URL and retry backoff are configured separately.
 */
@Configuration
public class GoogleMapsHttpConfiguration {

    /**
     * Backs {@code RouteMatrixClient#computeRouteMatrix} - only ever needs
     * originIndex/destinationIndex/status/condition/distanceMeters/duration ({@code duration} lets
     * {@code PickupMatchTasklet} check a driver can actually reach the pickup before its window closes, not just that
     * it's within range), and Google's docs call out that {@code status} must be included or every element silently
     * reports as OK.
     */
    @Bean(name = "googleMapsRestClient")
    public RestClient googleMapsRestClient(RestClient.Builder builder, GoogleMapsProperties googleMapsProperties) {
        return builder.baseUrl(googleMapsProperties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", googleMapsProperties.apiKey())
                .defaultHeader(
                        "X-Goog-FieldMask", "originIndex,destinationIndex,status,condition,distanceMeters,duration")
                .build();
    }

    /**
     * Backs {@code GoogleRoutesClient#computeRoute} - the Schedule page's manifest-route map, which needs actual route
     * geometry rather than just a distance/duration matrix. Every waypoint passed to that call already carries its own
     * lat/lng (every manifest stop is geocoded by Vektor itself), so unlike the matrix client above, nothing here needs
     * to read coordinates back out of Google's response.
     */
    @Bean(name = "googleRoutesComputeRestClient")
    public RestClient googleRoutesComputeRestClient(
            RestClient.Builder builder, GoogleMapsProperties googleMapsProperties) {
        return builder.baseUrl(googleMapsProperties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", googleMapsProperties.apiKey())
                .defaultHeader(
                        "X-Goog-FieldMask", "routes.polyline.encodedPolyline,routes.distanceMeters,routes.duration")
                .build();
    }

    /**
     * Backs {@code GooglePlacesClient#resolveDisplayName} - normalizes an address Google's Routes API can't geocode to
     * a drivable point into its place's canonical display name (see that class's javadoc), which is all this ever needs
     * back from Places API.
     */
    @Bean(name = "googlePlacesRestClient")
    public RestClient googlePlacesRestClient(
            RestClient.Builder builder,
            GoogleMapsProperties googleMapsProperties,
            GooglePlacesProperties googlePlacesProperties) {
        return builder.baseUrl(googlePlacesProperties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", googleMapsProperties.apiKey())
                .defaultHeader("X-Goog-FieldMask", "places.displayName")
                .build();
    }
}
