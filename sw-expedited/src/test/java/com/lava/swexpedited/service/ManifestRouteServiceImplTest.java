package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
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
    void findRoute_googleReturnsNoRoute_isEmpty() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.googleRoutesClient.computeRoute(List.of(PICKUP, DESTINATION))).thenReturn(Optional.empty());
        ManifestRouteServiceImpl service = service();

        assertThat(service.findRoute(1000589L)).isEmpty();
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
        when(this.googleRoutesClient.computeRoute(List.of(ORIGIN, PICKUP, DESTINATION)))
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
        verify(this.googleRoutesClient).computeRoute(eq(List.of(ORIGIN, PICKUP, DESTINATION)));
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
        when(this.googleRoutesClient.computeRoute(List.of(PICKUP, DRIVER_LOCATION, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(List.of(PICKUP, DRIVER_LOCATION, DESTINATION)));
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
        when(this.googleRoutesClient.computeRoute(List.of(PICKUP, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(List.of(PICKUP, DESTINATION)));
    }

    @Test
    void findRoute_driverLocationUnavailable_routeComputedWithoutIt() {
        VektorManifestStop pickup = stop(1, StopType.PICKUP, PICKUP.latitude(), PICKUP.longitude());
        VektorManifestStop dropoff = stop(2, StopType.DROPOFF, DESTINATION.latitude(), DESTINATION.longitude());
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, List.of(pickup, dropoff))));
        when(this.manifestDriverLocationService.findLiveLocation(1000589L)).thenReturn(Optional.empty());
        when(this.googleRoutesClient.computeRoute(List.of(PICKUP, DESTINATION)))
                .thenReturn(Optional.of(new ComputedRoute("abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service = service();

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        verify(this.googleRoutesClient).computeRoute(eq(List.of(PICKUP, DESTINATION)));
    }

    private ManifestRouteServiceImpl service() {
        return new ManifestRouteServiceImpl(
                this.vektorManifestRepository, this.googleRoutesClient, this.manifestDriverLocationService);
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
