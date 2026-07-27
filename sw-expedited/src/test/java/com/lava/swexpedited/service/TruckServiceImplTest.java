package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraVehicleDiagnosticsRepository;
import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.repository.SamsaraVehicleRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TruckServiceImplTest {

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private VektorDriverRepository vektorDriverRepository;

    @Mock
    private VektorTrailerRepository vektorTrailerRepository;

    @Mock
    private SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository;

    @Mock
    private SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    @Mock
    private SamsaraVehicleRepository samsaraVehicleRepository;

    @Test
    void findAll_truckWithDriverAndTrailer_includesResolvedNames() {
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of(truckRow("truck-1", "driver-1", "trailer-1")));
        when(this.vektorDriverRepository.findAll()).thenReturn(List.of(driverRow("driver-1", "Jane Trucker")));
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        List<TruckListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        TruckListingRow row = result.getFirst();
        assertThat(row.id()).isEqualTo("truck-1");
        assertThat(row.truckNumber()).isEqualTo("T1000");
        assertThat(row.currentDriverName()).isEqualTo("Jane Trucker");
        assertThat(row.currentTrailerLabel()).isEqualTo("T231 - 53' SDL");
    }

    @Test
    void findAll_truckWithNoDriverOrTrailer_resolvedFieldsAreNull() {
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of(truckRow("truck-1", null, null)));
        when(this.vektorDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of());
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        List<TruckListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        TruckListingRow row = result.getFirst();
        assertThat(row.currentDriverName()).isNull();
        assertThat(row.currentTrailerLabel()).isNull();
    }

    @Test
    void findAll_truckWithMatchedSamsaraVehicle_includesEngineStateAndEcuSpeed() {
        when(this.vektorTruckRepository.findAll())
                .thenReturn(List.of(truckRow("truck-1", null, null, "samsara-vehicle-1")));
        when(this.vektorDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of());
        when(this.samsaraVehicleDiagnosticsRepository.findAll())
                .thenReturn(List.of(new SamsaraVehicleDiagnosticsRow(
                        "samsara-vehicle-1",
                        62,
                        296451840L,
                        19483200L,
                        null,
                        "On",
                        62.5,
                        41000,
                        13200,
                        92220,
                        1200,
                        54,
                        LocalDateTime.now())));
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        List<TruckListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        TruckListingRow row = result.getFirst();
        assertThat(row.engineState()).isEqualTo("On");
        assertThat(row.ecuSpeedMph()).isEqualTo(62.5);
    }

    @Test
    void findAll_truckWithNoMatchedSamsaraVehicle_engineStateAndEcuSpeedAreNull() {
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of(truckRow("truck-1", null, null)));
        when(this.vektorDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of());

        List<TruckListingRow> result = new TruckServiceImpl(
                        this.vektorTruckRepository,
                        this.vektorDriverRepository,
                        this.vektorTrailerRepository,
                        this.samsaraVehicleDiagnosticsRepository,
                        this.samsaraVehicleLocationRepository,
                        this.samsaraVehicleRepository)
                .findAll();

        assertThat(result).hasSize(1);
        TruckListingRow row = result.getFirst();
        assertThat(row.engineState()).isNull();
        assertThat(row.ecuSpeedMph()).isNull();
    }

    @Test
    void findDetail_unknownTruckId_isEmpty() {
        when(this.vektorTruckRepository.findById("unknown")).thenReturn(Optional.empty());
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        assertThat(service.findDetail("unknown")).isEmpty();
    }

    @Test
    void findDetail_truckWithDriverAndTrailer_returnsFullyPopulatedResponse() {
        when(this.vektorTruckRepository.findById("truck-1"))
                .thenReturn(Optional.of(truckRow("truck-1", "driver-1", "trailer-1")));
        when(this.vektorDriverRepository.findById("driver-1"))
                .thenReturn(Optional.of(driverRow("driver-1", "Jane Trucker")));
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        Optional<TruckDetailResponse> result = service.findDetail("truck-1");

        assertThat(result).isPresent();
        TruckDetailResponse response = result.get();
        assertThat(response.id()).isEqualTo("truck-1");
        assertThat(response.vin()).isEqualTo("1FUJA6CV12LM12345");
        assertThat(response.currentDriverName()).isEqualTo("Jane Trucker");
        assertThat(response.currentTrailerLabel()).isEqualTo("T231 - 53' SDL");
    }

    @Test
    void findDetail_truckWithNoDriverOrTrailer_resolvedFieldsAreNull() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("truck-1", null, null)));
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        Optional<TruckDetailResponse> result = service.findDetail("truck-1");

        assertThat(result).isPresent();
        TruckDetailResponse response = result.get();
        assertThat(response.currentDriverName()).isNull();
        assertThat(response.currentTrailerLabel()).isNull();
        assertThat(response.licensePlate()).isNull();
    }

    @Test
    void findDetail_truckWithMatchedSamsaraVehicle_includesConvertedDiagnosticsAndLocation() {
        when(this.vektorTruckRepository.findById("truck-1"))
                .thenReturn(Optional.of(truckRow("truck-1", null, null, "samsara-vehicle-1")));
        when(this.samsaraVehicleDiagnosticsRepository.findByVehicleId("samsara-vehicle-1"))
                .thenReturn(Optional.of(new SamsaraVehicleDiagnosticsRow(
                        "samsara-vehicle-1",
                        62,
                        296451840L,
                        19483200L,
                        "{\"canBusType\":\"CANBUS_J1939_500\"}",
                        "On",
                        62.5,
                        41000,
                        13200,
                        92220,
                        1200,
                        54,
                        LocalDateTime.now())));
        when(this.samsaraVehicleLocationRepository.findByVehicleId("samsara-vehicle-1"))
                .thenReturn(Optional.of(new SamsaraVehicleLocationRow(
                        "samsara-vehicle-1",
                        "2401",
                        new BigDecimal("35.221"),
                        new BigDecimal("-101.831"),
                        null,
                        null,
                        LocalDateTime.of(2026, 7, 27, 3, 22, 0),
                        "I-40 near Amarillo, TX",
                        LocalDateTime.now())));
        when(this.samsaraVehicleRepository.findById("samsara-vehicle-1"))
                .thenReturn(Optional.of(new SamsaraVehicleRow(
                        "samsara-vehicle-1",
                        "1FUJA6CV12LM12345",
                        "2401",
                        "Freightliner",
                        "Cascadia",
                        "2023",
                        "6YA522",
                        "{}",
                        LocalDateTime.now())));
        TruckServiceImpl service = new TruckServiceImpl(
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorTrailerRepository,
                this.samsaraVehicleDiagnosticsRepository,
                this.samsaraVehicleLocationRepository,
                this.samsaraVehicleRepository);

        Optional<TruckDetailResponse> result = service.findDetail("truck-1");

        assertThat(result).isPresent();
        TruckDetailResponse response = result.get();
        assertThat(response.fuelPercent()).isEqualTo(62);
        assertThat(response.odometerMiles()).isEqualTo(296451840L / 1609.344);
        assertThat(response.engineHours()).isEqualTo(19483200L / 3600.0);
        assertThat(response.faultCodes()).isEqualTo("{\"canBusType\":\"CANBUS_J1939_500\"}");
        assertThat(response.engineState()).isEqualTo("On");
        assertThat(response.ecuSpeedMph()).isEqualTo(62.5);
        assertThat(response.defLevelPercent()).isEqualTo(41.0);
        assertThat(response.batteryVolts()).isEqualTo(13.2);
        assertThat(response.coolantTempF()).isEqualTo(92.22 * 9.0 / 5.0 + 32.0);
        assertThat(response.engineRpm()).isEqualTo(1200);
        assertThat(response.engineLoadPercent()).isEqualTo(54);
        assertThat(response.latitude()).isEqualTo(new BigDecimal("35.221"));
        assertThat(response.longitude()).isEqualTo(new BigDecimal("-101.831"));
        assertThat(response.formattedLocation()).isEqualTo("I-40 near Amarillo, TX");
        assertThat(response.licensePlate()).isEqualTo("6YA522");
        // SamsaraVehicleLocationRow.locationTime holds Samsara's raw UTC wall-clock digits with the offset already
        // stripped (see SamsaraTasklet#parseLocalDateTime) - the response reattaches ZoneOffset.UTC rather than the
        // JVM's default zone, so the frontend's DatePipe converts to the viewer's local time correctly regardless of
        // what zone this service happens to run in.
        assertThat(response.locationTime()).isEqualTo(Instant.parse("2026-07-27T03:22:00Z"));
    }

    private VektorTruckRow truckRow(String id, String currentDriverId, String currentTrailerId) {
        return truckRow(id, currentDriverId, currentTrailerId, null);
    }

    private VektorTruckRow truckRow(
            String id, String currentDriverId, String currentTrailerId, String matchedSamsaraVehicleId) {
        return new VektorTruckRow(
                id,
                "T1000",
                1,
                "1FUJA6CV12LM12345",
                "Freightliner",
                "Cascadia",
                2023,
                currentTrailerId,
                currentDriverId,
                "{\"id\":\"" + id + "\"}",
                LocalDateTime.now(),
                matchedSamsaraVehicleId);
    }

    private VektorDriverRow driverRow(String id, String fullName) {
        return new VektorDriverRow(
                id, "D1000", fullName, "jane@example.com", "555-0100", null, "{}", LocalDateTime.now());
    }

    private VektorTrailerRow trailerRow(String id, String label) {
        return new VektorTrailerRow(id, label, "Great Dane", 2022, "{}", LocalDateTime.now());
    }
}
