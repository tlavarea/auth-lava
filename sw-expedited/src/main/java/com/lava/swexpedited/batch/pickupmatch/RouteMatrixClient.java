package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.lava.swexpedited.batch.RetryingHttpClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Computes driving distance between shipment pickup locations and driver destinations via Google Routes API's
 * {@code computeRouteMatrix}, batching multiple origin/destination pairs into as few HTTP calls as possible rather than
 * one call per pair. Origins are always free-text addresses (shipment_listing.origin) and destinations are always
 * lat/lng (vektor_manifest's already-geocoded destination), so requests never need the app to geocode a shipment origin
 * itself - {@code computeRouteMatrix} accepts a mix of address and location waypoints in the same request.
 *
 * <p>Batched at {@value #BATCH_SIZE} origins x {@value #BATCH_SIZE} destinations per request - comfortably under both
 * of Google's per-request caps (origins x destinations <= 625; and, separately, addresses/place IDs across origins and
 * destinations combined <= 50, which in this app's case only ever counts the origins since destinations are always
 * lat/lng). Response indices are corrected back to the caller's original, un-batched list positions before returning,
 * so callers never need to know batching happened.
 *
 * <p>Request/response DTOs here are plain records with no Jackson annotations beyond {@code @JsonInclude} (a
 * jackson-annotations class, unaffected by the Jackson 2/3 split - see {@code GfmBidClient}'s javadoc), so they're
 * (de)serialized by Spring's own auto-configured (Jackson 3) RestClient message converters rather than a manually built
 * ObjectMapper - unlike the GFM/Vektor clients, nothing here deserializes into a jsonschema2pojo-generated
 * Jackson-2-annotated class.
 */
@Component
public class RouteMatrixClient extends RetryingHttpClient {

    private static final int BATCH_SIZE = 25;
    private static final BigDecimal METERS_PER_MILE = BigDecimal.valueOf(1609.344);

    private final RestClient googleMapsRestClient;
    private final Duration retryBackoff;

    public RouteMatrixClient(
            @Qualifier("googleMapsRestClient") RestClient googleMapsRestClient,
            @Value("${google-maps.retry-backoff:5s}") Duration retryBackoff) {
        this.googleMapsRestClient = googleMapsRestClient;
        this.retryBackoff = retryBackoff;
    }

    /**
     * Returns one {@link RouteMatrixElement} per (origin, destination) pair across the full, un-batched
     * {@code originAddresses} x {@code destinations} matrix - {@code originIndex}/{@code destinationIndex} on each
     * element index into the lists as passed in, regardless of how many batched requests it took to compute them.
     */
    public List<RouteMatrixElement> computeRouteMatrix(List<String> originAddresses, List<LatLng> destinations) {
        List<RouteMatrixElement> results = new ArrayList<>();

        for (int originStart = 0; originStart < originAddresses.size(); originStart += BATCH_SIZE) {
            List<String> originBatch =
                    originAddresses.subList(originStart, Math.min(originStart + BATCH_SIZE, originAddresses.size()));

            for (int destinationStart = 0; destinationStart < destinations.size(); destinationStart += BATCH_SIZE) {
                List<LatLng> destinationBatch = destinations.subList(
                        destinationStart, Math.min(destinationStart + BATCH_SIZE, destinations.size()));

                for (RouteMatrixElement element : computeBatch(originBatch, destinationBatch)) {
                    results.add(new RouteMatrixElement(
                            element.originIndex() + originStart,
                            element.destinationIndex() + destinationStart,
                            element.distanceMeters(),
                            element.condition(),
                            element.duration()));
                }
            }
        }

        return results;
    }

    private List<RouteMatrixElement> computeBatch(List<String> originAddresses, List<LatLng> destinations) {
        RouteMatrixRequest request = new RouteMatrixRequest(
                originAddresses.stream()
                        .map(address -> new RouteMatrixOrigin(Waypoint.ofAddress(address)))
                        .toList(),
                destinations.stream()
                        .map(latLng -> new RouteMatrixDestination(Waypoint.ofLocation(latLng)))
                        .toList(),
                "DRIVE");

        RouteMatrixElement[] elements = retrying(
                () -> this.googleMapsRestClient
                        .post()
                        .uri("/distanceMatrix/v2:computeRouteMatrix")
                        .body(request)
                        .retrieve()
                        .body(RouteMatrixElement[].class),
                this.retryBackoff);

        return elements == null ? List.of() : List.of(elements);
    }

    record RouteMatrixRequest(
            List<RouteMatrixOrigin> origins, List<RouteMatrixDestination> destinations, String travelMode) {}

    record RouteMatrixOrigin(Waypoint waypoint) {}

    record RouteMatrixDestination(Waypoint waypoint) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Waypoint(String address, Location location) {
        static Waypoint ofAddress(String address) {
            return new Waypoint(address, null);
        }

        static Waypoint ofLocation(LatLng latLng) {
            return new Waypoint(null, new Location(latLng));
        }
    }

    record Location(LatLng latLng) {}

    /** Public - it's part of {@link #computeRouteMatrix}'s public signature, unlike the other DTOs here. */
    public record LatLng(BigDecimal latitude, BigDecimal longitude) {}

    /**
     * Public - it's part of {@link #computeRouteMatrix}'s public signature, unlike the other DTOs here.
     * {@code distanceMeters}/{@code duration} are null when {@code condition} isn't {@code ROUTE_EXISTS} (e.g. no
     * drivable route between the two points) - Google's field mask always includes {@code status}, per the API's own
     * warning that omitting it makes every element look like a success, but this app has no use for status's contents
     * beyond that mask requirement, so it isn't modeled here; {@code @JsonIgnoreProperties} tolerates it (and anything
     * else Google adds) showing up in the response regardless of the app's global unknown-property deserialization
     * setting. {@code duration} comes back as a protobuf-style string like {@code "1234s"} (occasionally with a
     * fractional-second component) rather than an ISO-8601 duration, so it can't be deserialized straight into a
     * {@link Duration} - {@link #durationValue()} parses it.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteMatrixElement(
            int originIndex, int destinationIndex, Long distanceMeters, String condition, String duration) {

        public boolean routeExists() {
            return "ROUTE_EXISTS".equals(this.condition);
        }

        public BigDecimal distanceMiles() {
            return this.distanceMeters == null
                    ? null
                    : BigDecimal.valueOf(this.distanceMeters).divide(METERS_PER_MILE, 1, RoundingMode.HALF_UP);
        }

        public Duration durationValue() {
            if (this.duration == null) {
                return null;
            }
            BigDecimal seconds = new BigDecimal(this.duration.substring(0, this.duration.length() - 1));
            return Duration.ofNanos(seconds.movePointRight(9).longValue());
        }
    }
}
