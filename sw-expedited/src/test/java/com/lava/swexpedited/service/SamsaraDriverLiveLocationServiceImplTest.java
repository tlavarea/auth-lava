package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.model.ReverseGeo;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamsaraDriverLiveLocationServiceImplTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    @Test
    void findLiveLocation_driverWithNoAssignment_isEmpty() {
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.empty());
        SamsaraDriverLiveLocationServiceImpl service = new SamsaraDriverLiveLocationServiceImpl(
                this.samsaraFleetClient, this.samsaraDriverVehicleAssignmentRepository);

        assertThat(service.findLiveLocation("41000123")).isEmpty();
    }

    @Test
    void findLiveLocation_vehicleWithNoGpsPayload_isEmpty() {
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.of(assignmentRow("41000123", "281474")));
        when(this.samsaraFleetClient.fetchVehicleLocation("281474"))
                .thenReturn(List.of(new VehicleStatsResponseData().id("281474").name("Truck 12")));
        SamsaraDriverLiveLocationServiceImpl service = new SamsaraDriverLiveLocationServiceImpl(
                this.samsaraFleetClient, this.samsaraDriverVehicleAssignmentRepository);

        assertThat(service.findLiveLocation("41000123")).isEmpty();
    }

    @Test
    void findLiveLocation_driverWithAssignmentAndGps_returnsLiveLocation() {
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.of(assignmentRow("41000123", "281474")));
        VehicleStatsGps gps = new VehicleStatsGps()
                .latitude(32.735)
                .longitude(-97.108)
                .headingDegrees(180.5)
                .speedMilesPerHour(62.3)
                .time("2026-07-16T12:00:00Z")
                .reverseGeo(new ReverseGeo().formattedLocation("Fort Worth, TX"));
        when(this.samsaraFleetClient.fetchVehicleLocation("281474"))
                .thenReturn(List.of(new VehicleStatsResponseData()
                        .id("281474")
                        .name("Truck 12")
                        .gps(gps)));
        SamsaraDriverLiveLocationServiceImpl service = new SamsaraDriverLiveLocationServiceImpl(
                this.samsaraFleetClient, this.samsaraDriverVehicleAssignmentRepository);

        Optional<DriverLiveLocationResponse> result = service.findLiveLocation("41000123");

        assertThat(result).isPresent();
        DriverLiveLocationResponse liveLocation = result.get();
        assertThat(liveLocation.latitude()).isEqualByComparingTo("32.735");
        assertThat(liveLocation.longitude()).isEqualByComparingTo("-97.108");
        assertThat(liveLocation.heading()).isEqualByComparingTo("180.5");
        assertThat(liveLocation.speed()).isEqualByComparingTo("62.3");
        assertThat(liveLocation.formattedLocation()).isEqualTo("Fort Worth, TX");
        assertThat(liveLocation.locationTime()).isEqualTo(LocalDateTime.of(2026, 7, 16, 12, 0, 0));
    }

    private SamsaraDriverVehicleAssignmentRow assignmentRow(String driverId, String vehicleId) {
        return new SamsaraDriverVehicleAssignmentRow(
                driverId,
                vehicleId,
                "Truck 12",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
