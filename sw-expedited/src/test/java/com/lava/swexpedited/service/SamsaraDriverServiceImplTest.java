package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamsaraDriverServiceImplTest {

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    @Mock
    private SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    @Mock
    private SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    @Test
    void findAll_driverWithAssignmentAndLocationAndDutyStatus_includesAllJoinedFields() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverVehicleAssignmentRepository.findAll())
                .thenReturn(List.of(assignmentRow("41000123", "281474", "Truck 12")));
        when(this.samsaraVehicleLocationRepository.findAll()).thenReturn(List.of(locationRow("281474")));
        when(this.samsaraDriverDutyStatusRepository.findAll())
                .thenReturn(List.of(dutyStatusRow("41000123", "driving")));
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        List<DriverListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        DriverListingRow row = result.getFirst();
        assertThat(row.id()).isEqualTo("41000123");
        assertThat(row.name()).isEqualTo("Jane Trucker");
        assertThat(row.activationStatus()).isEqualTo("active");
        assertThat(row.currentVehicleName()).isEqualTo("Truck 12");
        assertThat(row.dutyStatus()).isEqualTo("driving");
        assertThat(row.currentLocation()).isEqualTo("Fort Worth, TX");
    }

    @Test
    void findAll_driverWithNoAssignment_currentVehicleNameAndLocationAreNull() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverVehicleAssignmentRepository.findAll()).thenReturn(List.of());
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        List<DriverListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        DriverListingRow row = result.getFirst();
        assertThat(row.currentVehicleName()).isNull();
        assertThat(row.currentLocation()).isNull();
        assertThat(row.dutyStatus()).isNull();
    }

    @Test
    void findDetail_unknownDriverId_isEmpty() {
        when(this.samsaraDriverRepository.findById("unknown")).thenReturn(Optional.empty());
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        assertThat(service.findDetail("unknown")).isEmpty();
    }

    @Test
    void findDetail_driverWithAssignmentAndLocationAndDutyStatus_returnsFullyPopulatedResponse() {
        when(this.samsaraDriverRepository.findById("41000123")).thenReturn(Optional.of(driverRow("41000123")));
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.of(assignmentRow("41000123", "281474", "Truck 12")));
        when(this.samsaraVehicleLocationRepository.findByVehicleId("281474"))
                .thenReturn(Optional.of(locationRow("281474")));
        when(this.samsaraDriverDutyStatusRepository.findByDriverId("41000123"))
                .thenReturn(Optional.of(dutyStatusRow("41000123", "driving")));
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        Optional<DriverDetailResponse> result = service.findDetail("41000123");

        assertThat(result).isPresent();
        DriverDetailResponse response = result.get();
        assertThat(response.id()).isEqualTo("41000123");
        assertThat(response.currentVehicleId()).isEqualTo("281474");
        assertThat(response.currentVehicleName()).isEqualTo("Truck 12");
        assertThat(response.dutyStatus()).isEqualTo("driving");
        assertThat(response.latitude()).isEqualByComparingTo("32.735000");
        assertThat(response.longitude()).isEqualByComparingTo("-97.108000");
        assertThat(response.formattedLocation()).isEqualTo("Fort Worth, TX");
        assertThat(response.rawResponse()).isEqualTo("{\"id\":\"41000123\"}");
    }

    @Test
    void findDetail_driverWithNoAssignment_assignmentAndLocationFieldsAreNull() {
        when(this.samsaraDriverRepository.findById("41000123")).thenReturn(Optional.of(driverRow("41000123")));
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.empty());
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        Optional<DriverDetailResponse> result = service.findDetail("41000123");

        assertThat(result).isPresent();
        DriverDetailResponse response = result.get();
        assertThat(response.currentVehicleId()).isNull();
        assertThat(response.currentVehicleName()).isNull();
        assertThat(response.latitude()).isNull();
        assertThat(response.longitude()).isNull();
        assertThat(response.locationTime()).isNull();
        assertThat(response.formattedLocation()).isNull();
        assertThat(response.dutyStatus()).isNull();
    }

    @Test
    void findDetail_assignedVehicleHasNoSyncedLocation_assignmentPopulatedLocationNull() {
        when(this.samsaraDriverRepository.findById("41000123")).thenReturn(Optional.of(driverRow("41000123")));
        when(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .thenReturn(Optional.of(assignmentRow("41000123", "281474", "Truck 12")));
        when(this.samsaraVehicleLocationRepository.findByVehicleId("281474")).thenReturn(Optional.empty());
        SamsaraDriverServiceImpl service = new SamsaraDriverServiceImpl(
                this.samsaraDriverRepository,
                this.samsaraDriverVehicleAssignmentRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraDriverDutyStatusRepository);

        Optional<DriverDetailResponse> result = service.findDetail("41000123");

        assertThat(result).isPresent();
        DriverDetailResponse response = result.get();
        assertThat(response.currentVehicleId()).isEqualTo("281474");
        assertThat(response.currentVehicleName()).isEqualTo("Truck 12");
        assertThat(response.latitude()).isNull();
        assertThat(response.longitude()).isNull();
        assertThat(response.locationTime()).isNull();
        assertThat(response.formattedLocation()).isNull();
    }

    private SamsaraDriverRow driverRow(String id) {
        return new SamsaraDriverRow(
                id,
                "Jane Trucker",
                "jtrucker",
                "jane.trucker@example.com",
                "555-0100",
                "D1234567",
                "TX",
                "active",
                "expedited",
                LocalDateTime.now().minusMonths(6),
                LocalDateTime.now().minusDays(1),
                "{\"id\":\"" + id + "\"}",
                LocalDateTime.now());
    }

    private SamsaraDriverVehicleAssignmentRow assignmentRow(String driverId, String vehicleId, String vehicleName) {
        return new SamsaraDriverVehicleAssignmentRow(
                driverId,
                vehicleId,
                vehicleName,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private SamsaraDriverDutyStatusRow dutyStatusRow(String driverId, String dutyStatus) {
        return new SamsaraDriverDutyStatusRow(driverId, dutyStatus, LocalDateTime.now());
    }

    private SamsaraVehicleLocationRow locationRow(String vehicleId) {
        return new SamsaraVehicleLocationRow(
                vehicleId,
                "Truck 12",
                new BigDecimal("32.735000"),
                new BigDecimal("-97.108000"),
                new BigDecimal("180.50"),
                new BigDecimal("62.30"),
                LocalDateTime.now().minusMinutes(1),
                "Fort Worth, TX",
                LocalDateTime.now());
    }
}
