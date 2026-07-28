package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraTrailerRepository;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import com.lava.swexpedited.samsara.SamsaraTrailerWithRaw;
import com.lava.swexpedited.samsara.model.Trailer;
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
class SamsaraTrailerSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraTrailerRepository samsaraTrailerRepository;

    @Test
    void execute_mapsPayloadsAndReplacesTable() {
        Trailer payload = new Trailer()
                .id("112")
                .name("1704")
                .licensePlate("34A1W4")
                .trailerSerialNumber("SN-112")
                .externalIds(Map.of("samsara.vin", "5MC125315H5165489"));
        when(this.samsaraFleetClient.fetchTrailers())
                .thenReturn(List.of(new SamsaraTrailerWithRaw(payload, "{\"id\":\"112\"}")));

        SamsaraTrailerSyncTasklet tasklet =
                new SamsaraTrailerSyncTasklet(this.samsaraFleetClient, this.samsaraTrailerRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraTrailerRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraTrailerRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraTrailerRow row = captor.getValue().getFirst();
        assertThat(row.id()).isEqualTo("112");
        assertThat(row.vin()).isEqualTo("5MC125315H5165489");
        assertThat(row.name()).isEqualTo("1704");
        assertThat(row.licensePlate()).isEqualTo("34A1W4");
        assertThat(row.trailerSerialNumber()).isEqualTo("SN-112");
        assertThat(row.rawResponse()).isEqualTo("{\"id\":\"112\"}");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void execute_noExternalIds_vinIsNull() {
        Trailer payload = new Trailer().id("112").name("1704");
        when(this.samsaraFleetClient.fetchTrailers())
                .thenReturn(List.of(new SamsaraTrailerWithRaw(payload, "{\"id\":\"112\"}")));

        SamsaraTrailerSyncTasklet tasklet =
                new SamsaraTrailerSyncTasklet(this.samsaraFleetClient, this.samsaraTrailerRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraTrailerRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraTrailerRepository).replaceAll(captor.capture());
        assertThat(captor.getValue().getFirst().vin()).isNull();
    }

    @Test
    void execute_externalIdsWithoutSamsaraVinKey_vinIsNull() {
        Trailer payload = new Trailer().id("112").name("1704").externalIds(Map.of("maintenanceId", "250020"));
        when(this.samsaraFleetClient.fetchTrailers())
                .thenReturn(List.of(new SamsaraTrailerWithRaw(payload, "{\"id\":\"112\"}")));

        SamsaraTrailerSyncTasklet tasklet =
                new SamsaraTrailerSyncTasklet(this.samsaraFleetClient, this.samsaraTrailerRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraTrailerRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraTrailerRepository).replaceAll(captor.capture());
        assertThat(captor.getValue().getFirst().vin()).isNull();
    }

    @Test
    void execute_emptyRoster_replacesTableWithEmptyList() {
        when(this.samsaraFleetClient.fetchTrailers()).thenReturn(List.of());

        SamsaraTrailerSyncTasklet tasklet =
                new SamsaraTrailerSyncTasklet(this.samsaraFleetClient, this.samsaraTrailerRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraTrailerRepository).replaceAll(List.of());
    }
}
