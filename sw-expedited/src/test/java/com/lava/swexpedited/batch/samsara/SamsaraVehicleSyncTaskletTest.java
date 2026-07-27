package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraVehicleRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import com.lava.swexpedited.samsara.SamsaraVehicleWithRaw;
import com.lava.swexpedited.samsara.model.Vehicle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class SamsaraVehicleSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraVehicleRepository samsaraVehicleRepository;

    @Test
    void execute_mapsPayloadsAndReplacesTable() {
        Vehicle payload = new Vehicle()
                .id("281474")
                .vin("1XPBD49X7ND764317")
                .name("2203")
                .make("PETERBILT")
                .model("579")
                .year("2022")
                .licensePlate("AN02697");
        when(this.samsaraFleetClient.fetchVehicles())
                .thenReturn(List.of(new SamsaraVehicleWithRaw(payload, "{\"id\":\"281474\"}")));

        SamsaraVehicleSyncTasklet tasklet =
                new SamsaraVehicleSyncTasklet(this.samsaraFleetClient, this.samsaraVehicleRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraVehicleRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraVehicleRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraVehicleRow row = captor.getValue().getFirst();
        assertThat(row.id()).isEqualTo("281474");
        assertThat(row.vin()).isEqualTo("1XPBD49X7ND764317");
        assertThat(row.name()).isEqualTo("2203");
        assertThat(row.make()).isEqualTo("PETERBILT");
        assertThat(row.model()).isEqualTo("579");
        assertThat(row.year()).isEqualTo("2022");
        assertThat(row.licensePlate()).isEqualTo("AN02697");
        assertThat(row.rawResponse()).isEqualTo("{\"id\":\"281474\"}");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void execute_emptyRoster_replacesTableWithEmptyList() {
        when(this.samsaraFleetClient.fetchVehicles()).thenReturn(List.of());

        SamsaraVehicleSyncTasklet tasklet =
                new SamsaraVehicleSyncTasklet(this.samsaraFleetClient, this.samsaraVehicleRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraVehicleRepository).replaceAll(List.of());
    }
}
