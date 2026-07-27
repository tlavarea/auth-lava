package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.model.ReverseGeo;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.truck.TruckRouteHistoryResponse;
import com.lava.swexpedited.truck.TruckRouteStop;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TruckRouteHistoryServiceImplTest {

    private static final Instant WINDOW_START = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-28T00:00:00Z");

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Test
    void findRouteHistory_unknownTruckId_isEmpty() {
        when(this.vektorTruckRepository.findById("bad-id")).thenReturn(Optional.empty());
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        assertThat(service.findRouteHistory("bad-id", WINDOW_START, WINDOW_END)).isEmpty();
    }

    @Test
    void findRouteHistory_truckWithNoMatchedVehicle_returnsEmptyLists() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow(null)));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        Optional<TruckRouteHistoryResponse> result = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END);

        assertThat(result).isPresent();
        assertThat(result.get().points()).isEmpty();
        assertThat(result.get().stops()).isEmpty();
    }

    @Test
    void findRouteHistory_longContiguousStop_producesOneStop() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        when(this.samsaraFleetClient.fetchVehicleGpsHistory("281474", WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        movingPoint("2026-07-27T12:00:00Z", 32.700, -97.000),
                        stoppedPoint("2026-07-27T12:05:00Z", 32.735, -97.108, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:10:00Z", 32.735, -97.108, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:20:00Z", 32.735, -97.108, "Fort Worth, TX"),
                        movingPoint("2026-07-27T12:30:00Z", 32.750, -97.120)));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckRouteStop> stops = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END)
                .orElseThrow()
                .stops();

        assertThat(stops).hasSize(1);
        TruckRouteStop stop = stops.getFirst();
        assertThat(stop.arrivalTime()).isEqualTo(Instant.parse("2026-07-27T12:05:00Z"));
        assertThat(stop.departureTime()).isEqualTo(Instant.parse("2026-07-27T12:20:00Z"));
        assertThat(stop.stoppedMinutes()).isEqualTo(15);
        assertThat(stop.formattedLocation()).isEqualTo("Fort Worth, TX");
    }

    @Test
    void findRouteHistory_briefStopUnderFiveMinutes_isDiscarded() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        when(this.samsaraFleetClient.fetchVehicleGpsHistory("281474", WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        movingPoint("2026-07-27T12:00:00Z", 32.700, -97.000),
                        stoppedPoint("2026-07-27T12:05:00Z", 32.735, -97.108, "Stop Sign Rd"),
                        stoppedPoint("2026-07-27T12:06:30Z", 32.735, -97.108, "Stop Sign Rd"),
                        movingPoint("2026-07-27T12:10:00Z", 32.750, -97.120)));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckRouteStop> stops = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END)
                .orElseThrow()
                .stops();

        assertThat(stops).isEmpty();
    }

    @Test
    void findRouteHistory_twoStopsSeparatedByMovement_areNotMerged() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        when(this.samsaraFleetClient.fetchVehicleGpsHistory("281474", WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        stoppedPoint("2026-07-27T08:00:00Z", 32.735, -97.108, "Origin, TX"),
                        stoppedPoint("2026-07-27T08:10:00Z", 32.735, -97.108, "Origin, TX"),
                        movingPoint("2026-07-27T09:00:00Z", 32.800, -97.200),
                        movingPoint("2026-07-27T10:00:00Z", 33.000, -97.400),
                        stoppedPoint("2026-07-27T11:00:00Z", 33.100, -97.500, "Destination, TX"),
                        stoppedPoint("2026-07-27T11:10:00Z", 33.100, -97.500, "Destination, TX")));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckRouteStop> stops = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END)
                .orElseThrow()
                .stops();

        assertThat(stops).hasSize(2);
        assertThat(stops.get(0).formattedLocation()).isEqualTo("Origin, TX");
        assertThat(stops.get(1).formattedLocation()).isEqualTo("Destination, TX");
    }

    @Test
    void findRouteHistory_gpsJitterWithinRadius_staysOneStop() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        // Successive samples drift by a few meters (well under the 50m cluster radius) around a slowly-shifting
        // centroid, as real GPS noise would - should still collapse into a single stop.
        when(this.samsaraFleetClient.fetchVehicleGpsHistory("281474", WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        movingPoint("2026-07-27T12:00:00Z", 32.700, -97.000),
                        stoppedPoint("2026-07-27T12:05:00Z", 32.73500, -97.10800, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:07:00Z", 32.73502, -97.10798, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:09:00Z", 32.73498, -97.10803, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:11:00Z", 32.73503, -97.10801, "Fort Worth, TX"),
                        stoppedPoint("2026-07-27T12:20:00Z", 32.73499, -97.10799, "Fort Worth, TX"),
                        movingPoint("2026-07-27T12:30:00Z", 32.750, -97.120)));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckRouteStop> stops = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END)
                .orElseThrow()
                .stops();

        assertThat(stops).hasSize(1);
        assertThat(stops.getFirst().stoppedMinutes()).isEqualTo(15);
    }

    @Test
    void findRouteHistory_connectivityGapWhileParked_stillMergesIntoOneStop() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        // No samples for several hours in the middle of the stop (e.g. the vehicle lost connectivity while parked) -
        // location matches before and after the gap, so this should still be one stop, not two.
        when(this.samsaraFleetClient.fetchVehicleGpsHistory("281474", WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        movingPoint("2026-07-27T20:00:00Z", 32.700, -97.000),
                        stoppedPoint("2026-07-27T20:05:00Z", 32.735, -97.108, "Truck Stop, TX"),
                        stoppedPoint("2026-07-27T20:10:00Z", 32.735, -97.108, "Truck Stop, TX"),
                        stoppedPoint("2026-07-28T04:00:00Z", 32.735, -97.108, "Truck Stop, TX"),
                        movingPoint("2026-07-28T04:30:00Z", 32.750, -97.120)));
        TruckRouteHistoryServiceImpl service =
                new TruckRouteHistoryServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckRouteStop> stops = service.findRouteHistory("truck-1", WINDOW_START, WINDOW_END)
                .orElseThrow()
                .stops();

        assertThat(stops).hasSize(1);
        assertThat(stops.getFirst().arrivalTime()).isEqualTo(Instant.parse("2026-07-27T20:05:00Z"));
        assertThat(stops.getFirst().departureTime()).isEqualTo(Instant.parse("2026-07-28T04:00:00Z"));
    }

    private static VehicleStatsGps stoppedPoint(String time, double lat, double lon, String formattedLocation) {
        return new VehicleStatsGps()
                .time(time)
                .latitude(lat)
                .longitude(lon)
                .speedMilesPerHour(0.0)
                .isEcuSpeed(true)
                .reverseGeo(new ReverseGeo().formattedLocation(formattedLocation));
    }

    private static VehicleStatsGps movingPoint(String time, double lat, double lon) {
        return new VehicleStatsGps()
                .time(time)
                .latitude(lat)
                .longitude(lon)
                .speedMilesPerHour(45.0)
                .isEcuSpeed(true);
    }

    private static VektorTruckRow truckRow(String matchedSamsaraVehicleId) {
        return new VektorTruckRow(
                "truck-1",
                "1234",
                1,
                "1XPBD49X7ND764317",
                "Peterbilt",
                "579",
                2022,
                null,
                null,
                "{}",
                LocalDateTime.now(),
                matchedSamsaraVehicleId);
    }
}
