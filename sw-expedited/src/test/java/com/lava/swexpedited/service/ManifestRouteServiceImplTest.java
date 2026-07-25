package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.pickupmatch.GooglePlacesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.RouteWaypoint;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.manifest.ManifestStopResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.StopType;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorManifestStartingPosition;
import com.lava.swexpedited.vektor.VektorManifestStop;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManifestRouteServiceImplTest {

    private static final LatLng ORIGIN = new LatLng(new BigDecimal("31.19"), new BigDecimal("-81.47"));
    private static final LatLng PICKUP = new LatLng(new BigDecimal("32.16"), new BigDecimal("-81.23"));
    private static final LatLng DESTINATION = new LatLng(new BigDecimal("29.57"), new BigDecimal("-97.93"));
    private static final LatLng DRIVER_LOCATION = new LatLng(new BigDecimal("30.0"), new BigDecimal("-90.0"));

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private GoogleRoutesClient googleRoutesClient;

    @Mock
    private GooglePlacesClient googlePlacesClient;

    @Mock
    private ManifestDriverLocationService manifestDriverLocationService;

    @Test
    void findRoute_manifestNotFound_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L)).thenReturn(Optional.empty());
        ManifestRouteServiceImpl service = service();

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_manifestWithoutStops_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of())));
        ManifestRouteServiceImpl service = service();

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_onlyOneWaypointResolvable_isEmpty() {
        VektorManifestStop stopWithoutCoordinates = stop(1, StopType.PICKUP, null, null);
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(
                        manifestRow(null, List.of(stopWithoutCoordinates, stop(2, StopType.DROPOFF, null, null)))));
        ManifestRouteServiceImpl service = service();

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_googleReturnsNoRoute_returnsStopsWithoutRouteGeometry() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DESTINATION))).thenReturn(Optional.empty());
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        ManifestRouteResponse response = result.get();
        assertThat(response.stops()).hasSize(2);
        assertThat(response.encodedPolyline()).isNull();
        assertThat(response.distanceMeters()).isNull();
        assertThat(response.duration()).isNull();
    }

    @Test
    void findRoute_routeExists_ordersWaypointsStartingPositionFirstAndMapsResponse() {
        VektorManifestStartingPosition startingPosition = new VektorManifestStartingPosition(
                "3314 Cypress Mill Rd, Brunswick, GA 31520",
                ORIGIN.latitude(),
                ORIGIN.longitude(),
                "Last stop of previous manifest #1000585",
                new BigDecimal("74.00"),
                new BigDecimal("174.00"),
                new BigDecimal("406543"));
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(startingPosition, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(exact(ORIGIN, PICKUP, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        ManifestRouteResponse response = result.get();
        assertThat(response.startingPosition().address()).isEqualTo("3314 Cypress Mill Rd, Brunswick, GA 31520");
        assertThat(response.stops()).hasSize(2);
        assertThat(response.stops())
                .extracting(ManifestStopResponse::sequenceNumber)
                .containsExactly(1, 2);
        assertThat(response.stops().getFirst().stopType()).isEqualTo(StopType.PICKUP);
        assertThat(response.encodedPolyline()).isEqualTo("abc123");
        assertThat(response.distanceMeters()).isEqualTo(160934L);
        assertThat(response.duration()).isEqualTo("7203.500s");
        verify(this.googleRoutesClient).computeRoute(eq(exact(ORIGIN, PICKUP, DESTINATION)));
        verify(this.googlePlacesClient, never()).resolveDisplayName(any());
    }

    @Test
    void findRoute_driverLocationKnown_insertedBeforeFirstIncompleteStop() {
        VektorManifestStop completedPickup =
                stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude(), LocalDateTime.of(2026, 7, 17, 11, 0));
        VektorManifestStop incompleteDropoff =
                stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude(), null);
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(completedPickup, incompleteDropoff))));
        when(this.manifestDriverLocationService.findLiveLocation(1000589L))
                .thenReturn(Optional.of(driverLiveLocation(DRIVER_LOCATION)));
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DRIVER_LOCATION, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(exact(PICKUP, DRIVER_LOCATION, DESTINATION)));
    }

    @Test
    void findRoute_allStopsAlreadyCheckedOut_driverLocationOmittedRatherThanReplacingDestination() {
        VektorManifestStop completedPickup =
                stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude(), LocalDateTime.of(2026, 7, 17, 11, 0));
        VektorManifestStop completedDropoff = stop(
                2,
                StopType.DROPOFF,
                DESTINATION.latitude(),
                DESTINATION.longitude(),
                LocalDateTime.of(2026, 7, 18, 9, 0));
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(completedPickup, completedDropoff))));
        when(this.manifestDriverLocationService.findLiveLocation(1000589L))
                .thenReturn(Optional.of(driverLiveLocation(DRIVER_LOCATION)));
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(exact(PICKUP, DESTINATION)));
    }

    @Test
    void findRoute_driverLocationUnavailable_routeComputedWithoutIt() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.manifestDriverLocationService.findLiveLocation(1000589L)).thenReturn(Optional.empty());
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(exact(PICKUP, DESTINATION)));
    }

    // Regression test for the Fort Hunter Liggett case: Google refuses the exact-coordinate request, but Places API
    // normalizes the stop's (possibly abbreviated) address to its place's canonical display name, and retrying with
    // that display name as a free-text address succeeds. The pickup's address deliberately doesn't resolve via
    // Places (mock defaults to Optional.empty()), so it stays coordinate-based on the retry - only the waypoint
    // Places actually resolved switches to an address.
    @Test
    void findRoute_googleReturnsNoRoute_placesResolvesAddress_retriesWithNormalizedAddressAndSucceeds() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = new VektorManifestStop(
                "stop-2",
                2,
                StopType.DROPOFF,
                "Fort Hunter Liggett",
                "238 California Avenue, FT H LIGGETT, CA 93928",
                DESTINATION.latitude(),
                DESTINATION.longitude(),
                "PDT",
                LocalDateTime.of(2026, 7, 24, 8, 0),
                LocalDateTime.of(2026, 7, 24, 14, 0),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DESTINATION))).thenReturn(Optional.empty());
        when(this.googlePlacesClient.resolveDisplayName("Address")).thenReturn(Optional.empty());
        when(this.googlePlacesClient.resolveDisplayName("238 California Avenue, FT H LIGGETT, CA 93928"))
                .thenReturn(Optional.of("Fort Hunter Liggett"));
        List<RouteWaypoint> retryWaypoints =
                List.of(RouteWaypoint.ofLocation(PICKUP), RouteWaypoint.ofAddress("Fort Hunter Liggett"));
        when(this.googleRoutesClient.computeRoute(retryWaypoints))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 127923L, "5019s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        ManifestRouteResponse response = result.get();
        assertThat(response.stops()).hasSize(2);
        assertThat(response.encodedPolyline()).isEqualTo("abc123");
        assertThat(response.distanceMeters()).isEqualTo(127923L);
        verify(this.googleRoutesClient).computeRoute(exact(PICKUP, DESTINATION));
        verify(this.googleRoutesClient).computeRoute(retryWaypoints);
    }

    // Places can't resolve either stop's address (e.g. siteName/address isn't a real geocodable place) - both are
    // tried, in order, but since neither produces a usable normalized address, no retry call to Routes API is made
    // at all (retrying with the exact same coordinates it already just rejected would be pointless).
    @Test
    void findRoute_googleReturnsNoRoute_placesCantResolveEitherAddress_noRetryRouteCallMade() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(exact(PICKUP, DESTINATION))).thenReturn(Optional.empty());
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        ManifestRouteResponse response = result.get();
        assertThat(response.stops()).hasSize(2);
        assertThat(response.encodedPolyline()).isNull();
        verify(this.googlePlacesClient, times(2)).resolveDisplayName("Address");
        verify(this.googleRoutesClient, times(1)).computeRoute(exact(PICKUP, DESTINATION));
    }

    // Regression test for the bug this replaced: normalizing every address-bearing waypoint in a single retry could
    // trade a working coordinate for a worse address elsewhere (Places API resolving a full, working address down to
    // a less specific display name) - here the starting position and dropoff are both already fine as coordinates,
    // and only the pickup (Ft Hunter Liggett) needs the fallback. If the retry swapped every address-bearing waypoint
    // at once, it would ask Routes API to route through the starting position's/dropoff's Places-resolved addresses
    // too, which aren't stubbed to return a usable route - so this only passes if exactly one waypoint is swapped at
    // a time, stopping as soon as one combination succeeds.
    @Test
    void findRoute_googleReturnsNoRoute_onlySwapsOneWaypointAtATimeUntilOneSucceeds() {
        VektorManifestStartingPosition startingPosition = new VektorManifestStartingPosition(
                "85PG+HP2, Baywood-Los Osos, CA", ORIGIN.latitude(), ORIGIN.longitude(), null, null, null, null);
        VektorManifestStop pickup = new VektorManifestStop(
                "stop-1",
                1,
                StopType.PICKUP,
                "Fort Hunter Liggett",
                "238 California Avenue, FT H LIGGETT, CA 93928",
                PICKUP.latitude(),
                PICKUP.longitude(),
                "PDT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(startingPosition, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(exact(ORIGIN, PICKUP, DESTINATION)))
                .thenReturn(Optional.empty());
        when(this.googlePlacesClient.resolveDisplayName("85PG+HP2, Baywood-Los Osos, CA"))
                .thenReturn(Optional.of("85PG+HP2"));
        when(this.googlePlacesClient.resolveDisplayName("238 California Avenue, FT H LIGGETT, CA 93928"))
                .thenReturn(Optional.of("Fort Hunter Liggett"));
        // Swapping the starting position alone (index 0) is tried first and fails - Places' resolved "85PG+HP2" is a
        // bare plus code with no city/state, not actually more routable than the original coordinate.
        when(this.googleRoutesClient.computeRoute(List.of(
                        RouteWaypoint.ofAddress("85PG+HP2"),
                        RouteWaypoint.ofLocation(PICKUP),
                        RouteWaypoint.ofLocation(DESTINATION))))
                .thenReturn(Optional.empty());
        // Swapping the pickup alone (index 1), with the starting position and dropoff left as coordinates, succeeds.
        List<RouteWaypoint> pickupSwapped = List.of(
                RouteWaypoint.ofLocation(ORIGIN),
                RouteWaypoint.ofAddress("Fort Hunter Liggett"),
                RouteWaypoint.ofLocation(DESTINATION));
        when(this.googleRoutesClient.computeRoute(pickupSwapped))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 127923L, "5019s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        assertThat(result.get().encodedPolyline()).isEqualTo("abc123");
        verify(this.googleRoutesClient).computeRoute(exact(ORIGIN, PICKUP, DESTINATION));
        verify(this.googleRoutesClient)
                .computeRoute(List.of(
                        RouteWaypoint.ofAddress("85PG+HP2"),
                        RouteWaypoint.ofLocation(PICKUP),
                        RouteWaypoint.ofLocation(DESTINATION)));
        verify(this.googleRoutesClient).computeRoute(pickupSwapped);
        // Never asked to normalize the dropoff's address - the loop stopped at the pickup, the second waypoint tried.
        verify(this.googlePlacesClient, never()).resolveDisplayName("Address");
    }

    private ManifestRouteServiceImpl service() {
        return new ManifestRouteServiceImpl(
                this.vektorManifestRepository,
                this.googleRoutesClient,
                this.googlePlacesClient,
                this.manifestDriverLocationService);
    }

    private static List<RouteWaypoint> exact(LatLng... points) {
        return Arrays.stream(points).map(RouteWaypoint::ofLocation).toList();
    }

    private ManifestDriverLocationResponse driverLiveLocation(LatLng location) {
        return new ManifestDriverLocationResponse(location.latitude(), location.longitude(), null, null, null);
    }

    private VektorManifestStop stop(int sequenceNumber, StopType stopType, BigDecimal latitude, BigDecimal longitude) {
        return stop(sequenceNumber, stopType, latitude, longitude, null);
    }

    private VektorManifestStop stop(
            int sequenceNumber,
            StopType stopType,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime checkedOutAt) {
        return new VektorManifestStop(
                "stop-" + sequenceNumber,
                sequenceNumber,
                stopType,
                "Site",
                "Address",
                latitude,
                longitude,
                "EDT",
                LocalDateTime.of(2026, 7, 17, 9, 30),
                LocalDateTime.of(2026, 7, 17, 10, 0),
                null,
                null,
                checkedOutAt,
                "CO 01660967",
                null,
                null,
                null,
                null,
                null);
    }

    private VektorManifestRow manifestRow(
            VektorManifestStartingPosition startingPosition, List<VektorManifestStop> stops) {
        return new VektorManifestRow(
                1000589L,
                "71da0ba8-865b-4c1a-8ad1-b95a4d2b8398",
                "b4a58cf3-150c-4ab8-9f9a-31a03da29bc2",
                "Warren Ruawhare",
                "5e0045bc-a89f-4ae8-beda-c40f1c0735cf",
                "41000123",
                "manifest_in_progress",
                "4251 Turin Dr, Bessemer, AL 35020",
                "6390 N Alsup Rd, Litchfield Park, AZ 85340",
                DESTINATION.latitude(),
                DESTINATION.longitude(),
                LocalDateTime.of(2026, 7, 17, 8, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0),
                "SwX-1000589",
                stops,
                startingPosition,
                "{}",
                null);
    }
}
