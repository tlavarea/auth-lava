package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
 * needs a single route per call, so there's no batching here. Waypoints are usually lat/lng - every manifest stop
 * carries its own already-geocoded coordinates (see {@code VektorManifestStop}) - but {@link RouteWaypoint} also
 * accepts a free-text address, for {@code ManifestRouteServiceImpl}'s retry when Google won't compute a drivable route
 * to/from a waypoint's exact coordinates: gated/restricted-access locations (e.g. a military installation) are
 * sometimes excluded from Google's public {@code DRIVE} routing entirely, even at coordinates Google's own Roads API
 * confirms it has indexed - re-geocoding a coarse address (rather than the precise coordinate) through this API's own
 * address resolution reliably lands on a routable point instead. See {@code ManifestRouteServiceImpl}'s javadoc for the
 * full retry flow.
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
    public Optional<ComputedRoute> computeRoute(List<RouteWaypoint> waypoints) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("computeRoute needs at least an origin and a destination waypoint");
        }

        ComputeRoutesRequest request = new ComputeRoutesRequest(
                toWaypoint(waypoints.getFirst()),
                toWaypoint(waypoints.getLast()),
                waypoints.subList(1, waypoints.size() - 1).stream()
                        .map(GoogleRoutesClient::toWaypoint)
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

    private static Waypoint toWaypoint(RouteWaypoint point) {
        return point.address() != null ? Waypoint.ofAddress(point.address()) : Waypoint.ofLocation(point.latLng());
    }

    /**
     * Public - constructed by {@code ManifestRouteServiceImpl} for every call into {@link #computeRoute}. Most
     * waypoints are {@link #ofLocation}; {@link #ofAddress} exists only for the address-based retry described in this
     * class's javadoc.
     */
    public record RouteWaypoint(RouteMatrixClient.LatLng latLng, String address) {
        public static RouteWaypoint ofLocation(RouteMatrixClient.LatLng latLng) {
            return new RouteWaypoint(latLng, null);
        }

        public static RouteWaypoint ofAddress(String address) {
            return new RouteWaypoint(null, address);
        }
    }

    record ComputeRoutesRequest(
            Waypoint origin, Waypoint destination, List<Waypoint> intermediates, String travelMode) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Waypoint(Location location, String address) {
        static Waypoint ofLocation(RouteMatrixClient.LatLng latLng) {
            return new Waypoint(new Location(latLng), null);
        }

        static Waypoint ofAddress(String address) {
            return new Waypoint(null, address);
        }
    }

    record Location(RouteMatrixClient.LatLng latLng) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComputeRoutesResponse(List<Route> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(Long distanceMeters, String duration, Polyline polyline) {}

    record Polyline(String encodedPolyline) {}

    /** Public - it's this client's return type. */
    public record ComputedRoute(String encodedPolyline, Long distanceMeters, String duration) {}
}
