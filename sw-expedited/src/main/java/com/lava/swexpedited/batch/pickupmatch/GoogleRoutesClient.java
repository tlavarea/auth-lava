package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lava.swexpedited.batch.RetryingHttpClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Computes an actual driving route (geometry, not just distance/duration) between a manifest's origin and destination
 * via Google Routes API's {@code computeRoutes}, backing the Schedule page's manifest-route map. Unlike
 * {@link RouteMatrixClient}, which batches many origin/destination pairs into one matrix call, this client only ever
 * needs a single pair per call, so there's no batching here. Origin is always a free-text address
 * (vektor_manifest.origin) and destination is always lat/lng (vektor_manifest's already-geocoded destination) - same
 * mixed-waypoint convention as {@link RouteMatrixClient}, and {@link RouteMatrixClient.LatLng} is reused directly here
 * rather than duplicated, since it's already public exactly for cross-client reuse of this data shape.
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
     * Returns the first route Google finds between {@code originAddress} and {@code destination}, or empty if Google
     * returns no drivable route. The returned {@link ComputedRoute}'s origin coordinates come from Google's response
     * (the {@code legs[0].startLocation} of the returned route) since this app never geocodes a manifest's origin
     * address itself.
     */
    public Optional<ComputedRoute> computeRoute(String originAddress, RouteMatrixClient.LatLng destination) {
        ComputeRoutesRequest request = new ComputeRoutesRequest(
                new Waypoint(originAddress, null), new Waypoint(null, new Location(destination)), "DRIVE");

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
        RouteMatrixClient.LatLng origin = route.legs() == null || route.legs().isEmpty()
                ? null
                : route.legs().getFirst().startLocation().latLng();

        return Optional.of(new ComputedRoute(
                origin == null ? null : origin.latitude(),
                origin == null ? null : origin.longitude(),
                route.polyline() == null ? null : route.polyline().encodedPolyline(),
                route.distanceMeters(),
                route.duration()));
    }

    record ComputeRoutesRequest(Waypoint origin, Waypoint destination, String travelMode) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Waypoint(String address, Location location) {}

    record Location(RouteMatrixClient.LatLng latLng) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComputeRoutesResponse(List<Route> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(Long distanceMeters, String duration, Polyline polyline, List<Leg> legs) {}

    record Polyline(String encodedPolyline) {}

    record Leg(Location startLocation) {}

    /**
     * Public - it's this client's return type. {@code originLatitude}/{@code originLongitude} are null only if Google's
     * response omits {@code legs} entirely, which shouldn't happen for a successful route but is tolerated rather than
     * thrown on.
     */
    public record ComputedRoute(
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            String encodedPolyline,
            Long distanceMeters,
            String duration) {}
}
