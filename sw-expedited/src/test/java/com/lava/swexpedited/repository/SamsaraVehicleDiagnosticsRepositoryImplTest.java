package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraVehicleDiagnosticsRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraVehicleDiagnosticsRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTableWithAllFields() {
        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(row("281474"), row("281475")));

        List<SamsaraVehicleDiagnosticsRow> found = this.samsaraVehicleDiagnosticsRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(SamsaraVehicleDiagnosticsRow::vehicleId)
                .containsExactlyInAnyOrder("281474", "281475");
        assertThat(found).allSatisfy(diagnostics -> {
            assertThat(diagnostics.syncedAt()).isNotNull();
            assertThat(diagnostics.fuelPercent()).isEqualTo(62);
            assertThat(diagnostics.odometerMeters()).isEqualTo(296451840L);
            assertThat(diagnostics.engineSeconds()).isEqualTo(19483200L);
            assertThat(diagnostics.faultCodes()).isEqualTo("{\"canBusType\":\"CANBUS_J1939_500\"}");
            assertThat(diagnostics.engineState()).isEqualTo("On");
            assertThat(diagnostics.ecuSpeedMph()).isEqualTo(62.5);
            assertThat(diagnostics.defLevelMilliPercent()).isEqualTo(41000);
            assertThat(diagnostics.batteryMilliVolts()).isEqualTo(13200);
            assertThat(diagnostics.coolantTempMilliC()).isEqualTo(92220);
            assertThat(diagnostics.engineRpm()).isEqualTo(1200);
            assertThat(diagnostics.engineLoadPercent()).isEqualTo(54);
        });
    }

    @Test
    void replaceAll_rowWithNullValueColumns_populatesTableWithNulls() {
        SamsaraVehicleDiagnosticsRow sparse = new SamsaraVehicleDiagnosticsRow(
                "281474", null, null, null, null, null, null, null, null, null, null, null, null);

        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(sparse));

        SamsaraVehicleDiagnosticsRow found = this.samsaraVehicleDiagnosticsRepository
                .findByVehicleId("281474")
                .orElseThrow();
        assertThat(found.fuelPercent()).isNull();
        assertThat(found.faultCodes()).isNull();
        assertThat(found.engineState()).isNull();
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(row("281474")));

        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(row("281475")));

        List<SamsaraVehicleDiagnosticsRow> found = this.samsaraVehicleDiagnosticsRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().vehicleId()).isEqualTo("281475");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(row("281474")));

        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of());

        assertThat(this.samsaraVehicleDiagnosticsRepository.findAll()).isEmpty();
    }

    @Test
    void findByVehicleId_noRow_isEmpty() {
        assertThat(this.samsaraVehicleDiagnosticsRepository.findByVehicleId("281474"))
                .isEmpty();
    }

    @Test
    void findByVehicleId_matchingRow_returnsIt() {
        this.samsaraVehicleDiagnosticsRepository.replaceAll(List.of(row("281474"), row("281475")));

        assertThat(this.samsaraVehicleDiagnosticsRepository.findByVehicleId("281474"))
                .isPresent()
                .get()
                .extracting(SamsaraVehicleDiagnosticsRow::vehicleId)
                .isEqualTo("281474");
    }

    private SamsaraVehicleDiagnosticsRow row(String vehicleId) {
        return new SamsaraVehicleDiagnosticsRow(
                vehicleId,
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
                LocalDateTime.now().minusMinutes(1));
    }
}
