package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lava.swexpedited.batch.RetryingHttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Computes an actual driving route (geometry, not just distance/duration) through an ordered list of waypoints via
 * Google Routes API's {@code computeRoutes}, backing the Schedule page's manifest-route map. Unlike
 * {@link RouteMatrixClient}, which batches many origin/destination pairs into one matrix call, this client only ever
 * needs a single route per call, so there's no batching here. Every waypoint is lat/lng - every manifest stop now
 * carries its own already-geocoded coordinates (see {@code VektorManifestStop}), so unlike the address-based origin
 * this client used to accept, there's no geocoding step left to do here at all.
 */
@Component
public class GoogleRoutesClient extends RetryingHttpClient {

    private final RestClient googleRoutesComputeRestClient;
    private final Duration retryBackoff;

    public GoogleRoutesClient(
            @Qualifier("googleRoutesComputeRestClient") RestClient googleRoutesComputeRestClient,
            @Value("${google-maps.retry-backoff:5s}") Duration retryBackoff) {
        this.googleRoutesComputeRestClient = googleRoutesComputeRestClient;
        this.retryBackoff = retryBackoff;
    }

    /**
     * Returns the first route Google finds through {@code waypoints} in order (first = origin, last = destination,
     * everything in between = an intermediate stop the route must pass through), or empty if Google returns no drivable
     * route. Requires at least two waypoints.
     */
    public Optional<ComputedRoute> computeRoute(List<RouteMatrixClient.LatLng> waypoints) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("computeRoute needs at least an origin and a destination waypoint");
        }

        ComputeRoutesRequest request = new ComputeRoutesRequest(
                new Waypoint(new Location(waypoints.getFirst())),
                new Waypoint(new Location(waypoints.getLast())),
                waypoints.subList(1, waypoints.size() - 1).stream()
                        .map(point -> new Waypoint(new Location(point)))
                        .toList(),
                "DRIVE");

        ComputeRoutesResponse response = retrying(
                () -> this.googleRoutesComputeRestClient
                        .post()
                        .uri("/directions/v2:computeRoutes")
                        .body(request)
                        .retrieve()
                        .body(ComputeRoutesResponse.class),
                this.retryBackoff);

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            return Optional.empty();
        }

        Route route = response.routes().getFirst();
        return Optional.of(new ComputedRoute(
                route.polyline() == null ? null : route.polyline().encodedPolyline(),
                route.distanceMeters(),
                route.duration()));
    }

    record ComputeRoutesRequest(
            Waypoint origin, Waypoint destination, List<Waypoint> intermediates, String travelMode) {}

    record Waypoint(Location location) {}

    record Location(RouteMatrixClient.LatLng latLng) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComputeRoutesResponse(List<Route> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(Long distanceMeters, String duration, Polyline polyline) {}

    record Polyline(String encodedPolyline) {}

    /** Public - it's this client's return type. */
    public record ComputedRoute(String encodedPolyline, Long distanceMeters, String duration) {}
}
