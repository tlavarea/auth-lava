package com.lava.swexpedited.configuration;

import com.lava.swexpedited.boot.autoconfigure.app.GoogleMapsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RestClients for Google's Routes API ({@code routes.googleapis.com}). Auth is a static API key rather than a
 * per-request bearer token, so it's set as a default header here rather than attached per call site the way Vektor's
 * runtime-obtained JWT is (see {@code VektorHttpConfiguration}). {@code X-Goog-FieldMask} is likewise fixed per bean
 * rather than passed per-call, since each bean here only ever backs one client hitting one Routes API method with a
 * fixed set of needed response fields (Google's docs require {@code X-Goog-FieldMask} on every request, with no
 * request-level way to vary it without also varying the header - so one bean per distinct field mask is the natural
 * unit here, not one bean shared across different needs). The injected RestClient.Builder is used as-is on both beans:
 * no custom request factory, per the project's established gotcha that calling .requestFactory(...) on it silently
 * breaks MockRestServiceServer-based tests.
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
     * geometry rather than just a distance/duration matrix. {@code routes.legs.startLocation} is included because
     * vektor_manifest only stores the destination's lat/lng (the origin is address-only); the origin pin's coordinates
     * for the map come back from Google's own route response instead.
     */
    @Bean(name = "googleRoutesComputeRestClient")
    public RestClient googleRoutesComputeRestClient(
            RestClient.Builder builder, GoogleMapsProperties googleMapsProperties) {
        return builder.baseUrl(googleMapsProperties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", googleMapsProperties.apiKey())
                .defaultHeader(
                        "X-Goog-FieldMask",
                        "routes.polyline.encodedPolyline,routes.distanceMeters,routes.duration,routes.legs.startLocation")
                .build();
    }
}
