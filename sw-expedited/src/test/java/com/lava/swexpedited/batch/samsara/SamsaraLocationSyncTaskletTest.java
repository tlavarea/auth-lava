package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import com.lava.swexpedited.samsara.model.ReverseGeo;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class SamsaraLocationSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    @Test
    void execute_mapsLocationPayloadsAndReplacesTable() {
        VehicleStatsGps gps = new VehicleStatsGps()
                .latitude(32.735)
                .longitude(-97.108)
                .headingDegrees(180.5)
                .speedMilesPerHour(62.3)
                .time("2026-07-16T12:00:00Z")
                .reverseGeo(new ReverseGeo().formattedLocation("Fort Worth, TX"));
        VehicleStatsResponseData payload =
                new VehicleStatsResponseData().id("281474").name("Truck 12").gps(gps);
        when(this.samsaraFleetClient.fetchVehicleLocations()).thenReturn(List.of(payload));

        SamsaraLocationSyncTasklet tasklet =
                new SamsaraLocationSyncTasklet(this.samsaraFleetClient, this.samsaraVehicleLocationRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraVehicleLocationRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraVehicleLocationRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraVehicleLocationRow row = captor.getValue().getFirst();
        assertThat(row.vehicleId()).isEqualTo("281474");
        assertThat(row.vehicleName()).isEqualTo("Truck 12");
        assertThat(row.latitude()).isEqualByComparingTo("32.735");
        assertThat(row.longitude()).isEqualByComparingTo("-97.108");
        assertThat(row.heading()).isEqualByComparingTo("180.5");
        assertThat(row.speed()).isEqualByComparingTo("62.3");
        assertThat(row.locationTime()).isEqualTo(LocalDateTime.of(2026, 7, 16, 12, 0, 0));
        assertThat(row.formattedLocation()).isEqualTo("Fort Worth, TX");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void execute_locationWithNoReverseGeo_formattedLocationIsNull() {
        VehicleStatsGps gps =
                new VehicleStatsGps().latitude(32.735).longitude(-97.108).time("2026-07-16T12:00:00Z");
        VehicleStatsResponseData payload =
                new VehicleStatsResponseData().id("281474").name("Truck 12").gps(gps);
        when(this.samsaraFleetClient.fetchVehicleLocations()).thenReturn(List.of(payload));

        SamsaraLocationSyncTasklet tasklet =
                new SamsaraLocationSyncTasklet(this.samsaraFleetClient, this.samsaraVehicleLocationRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraVehicleLocationRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraVehicleLocationRepository).replaceAll(captor.capture());
        assertThat(captor.getValue().getFirst().formattedLocation()).isNull();
    }

    @Test
    void execute_vehicleWithNullGps_filteredOut() {
        VehicleStatsResponseData noGps =
                new VehicleStatsResponseData().id("281474").name("Truck 12");
        when(this.samsaraFleetClient.fetchVehicleLocations()).thenReturn(List.of(noGps));

        SamsaraLocationSyncTasklet tasklet =
                new SamsaraLocationSyncTasklet(this.samsaraFleetClient, this.samsaraVehicleLocationRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraVehicleLocationRepository).replaceAll(List.of());
    }
}
