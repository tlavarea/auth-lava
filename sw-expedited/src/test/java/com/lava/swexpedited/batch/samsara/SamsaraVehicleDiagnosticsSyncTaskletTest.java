package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraVehicleDiagnosticsRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import com.lava.swexpedited.samsara.model.VehicleStatsBatteryVoltage;
import com.lava.swexpedited.samsara.model.VehicleStatsDefLevelMilliPercent;
import com.lava.swexpedited.samsara.model.VehicleStatsEcuSpeedMph;
import com.lava.swexpedited.samsara.model.VehicleStatsEngineCoolantTempMilliC;
import com.lava.swexpedited.samsara.model.VehicleStatsEngineLoadPercent;
import com.lava.swexpedited.samsara.model.VehicleStatsEngineRpm;
import com.lava.swexpedited.samsara.model.VehicleStatsEngineState;
import com.lava.swexpedited.samsara.model.VehicleStatsEngineStateSetting;
import com.lava.swexpedited.samsara.model.VehicleStatsFuelPercent;
import com.lava.swexpedited.samsara.model.VehicleStatsObdEngineSeconds;
import com.lava.swexpedited.samsara.model.VehicleStatsObdOdometerMeters;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class SamsaraVehicleDiagnosticsSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository;

    @Test
    void execute_mapsFullyPopulatedPayloadAndReplacesTable() {
        VehicleStatsResponseData payload = new VehicleStatsResponseData()
                .id("281474")
                .name("Truck 12")
                .fuelPercent(new VehicleStatsFuelPercent()
                        .time("2026-07-16T12:00:00Z")
                        .value(62L))
                .obdOdometerMeters(new VehicleStatsObdOdometerMeters()
                        .time("2026-07-16T12:00:00Z")
                        .value(296451840L))
                .obdEngineSeconds(new VehicleStatsObdEngineSeconds()
                        .time("2026-07-16T12:00:00Z")
                        .value(19483200L))
                .faultCodes(Map.of("canBusType", "CANBUS_J1939_500"))
                .engineState(new VehicleStatsEngineState()
                        .time("2026-07-16T12:00:00Z")
                        .value(VehicleStatsEngineStateSetting.ON))
                .ecuSpeedMph(new VehicleStatsEcuSpeedMph()
                        .time("2026-07-16T12:00:00Z")
                        .value(62.5))
                .defLevelMilliPercent(new VehicleStatsDefLevelMilliPercent()
                        .time("2026-07-16T12:00:00Z")
                        .value(41000L))
                .batteryMilliVolts(new VehicleStatsBatteryVoltage()
                        .time("2026-07-16T12:00:00Z")
                        .value(13200L))
                .engineCoolantTemperatureMilliC(new VehicleStatsEngineCoolantTempMilliC()
                        .time("2026-07-16T12:00:00Z")
                        .value(92220L))
                .engineRpm(
                        new VehicleStatsEngineRpm().time("2026-07-16T12:00:00Z").value(1200L))
                .engineLoadPercent(new VehicleStatsEngineLoadPercent()
                        .time("2026-07-16T12:00:00Z")
                        .value(54L));
        when(this.samsaraFleetClient.fetchVehicleDiagnostics()).thenReturn(List.of(payload));

        SamsaraVehicleDiagnosticsSyncTasklet tasklet = new SamsaraVehicleDiagnosticsSyncTasklet(
                this.samsaraFleetClient, this.samsaraVehicleDiagnosticsRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraVehicleDiagnosticsRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraVehicleDiagnosticsRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraVehicleDiagnosticsRow row = captor.getValue().getFirst();
        assertThat(row.vehicleId()).isEqualTo("281474");
        assertThat(row.fuelPercent()).isEqualTo(62);
        assertThat(row.odometerMeters()).isEqualTo(296451840L);
        assertThat(row.engineSeconds()).isEqualTo(19483200L);
        assertThat(row.faultCodes()).contains("\"canBusType\":\"CANBUS_J1939_500\"");
        assertThat(row.engineState()).isEqualTo("On");
        assertThat(row.ecuSpeedMph()).isEqualTo(62.5);
        assertThat(row.defLevelMilliPercent()).isEqualTo(41000);
        assertThat(row.batteryMilliVolts()).isEqualTo(13200);
        assertThat(row.coolantTempMilliC()).isEqualTo(92220);
        assertThat(row.engineRpm()).isEqualTo(1200);
        assertThat(row.engineLoadPercent()).isEqualTo(54);
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void execute_payloadWithNoFieldsReported_allValueColumnsAreNull() {
        VehicleStatsResponseData payload =
                new VehicleStatsResponseData().id("281474").name("Truck 12");
        when(this.samsaraFleetClient.fetchVehicleDiagnostics()).thenReturn(List.of(payload));

        SamsaraVehicleDiagnosticsSyncTasklet tasklet = new SamsaraVehicleDiagnosticsSyncTasklet(
                this.samsaraFleetClient, this.samsaraVehicleDiagnosticsRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraVehicleDiagnosticsRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraVehicleDiagnosticsRepository).replaceAll(captor.capture());
        SamsaraVehicleDiagnosticsRow row = captor.getValue().getFirst();
        assertThat(row.vehicleId()).isEqualTo("281474");
        assertThat(row.fuelPercent()).isNull();
        assertThat(row.odometerMeters()).isNull();
        assertThat(row.engineSeconds()).isNull();
        assertThat(row.faultCodes()).isNull();
        assertThat(row.engineState()).isNull();
        assertThat(row.ecuSpeedMph()).isNull();
        assertThat(row.defLevelMilliPercent()).isNull();
        assertThat(row.batteryMilliVolts()).isNull();
        assertThat(row.coolantTempMilliC()).isNull();
        assertThat(row.engineRpm()).isNull();
        assertThat(row.engineLoadPercent()).isNull();
    }

    @Test
    void execute_emptyDiagnostics_replacesTableWithEmptyList() {
        when(this.samsaraFleetClient.fetchVehicleDiagnostics()).thenReturn(List.of());

        SamsaraVehicleDiagnosticsSyncTasklet tasklet = new SamsaraVehicleDiagnosticsSyncTasklet(
                this.samsaraFleetClient, this.samsaraVehicleDiagnosticsRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraVehicleDiagnosticsRepository).replaceAll(List.of());
    }
}
