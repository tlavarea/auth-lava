package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.model.CurrentDutyStatus;
import com.lava.swexpedited.samsara.model.DriverTinyResponse;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class SamsaraDriverDutyStatusSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    @Test
    void execute_scopesFetchToSyncedRosterAndMapsDutyStatuses() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType("driving"));
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(hosClocksForDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<SamsaraDriverDutyStatusRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        SamsaraDriverDutyStatusRow row = captor.getValue().getFirst();
        assertThat(row.driverId()).isEqualTo("41000123");
        assertThat(row.dutyStatus()).isEqualTo("driving");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void execute_disconnectedAppEmptyStatus_mapsToNull() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType(""));
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(hosClocksForDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverDutyStatusRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(captor.capture());
        assertThat(captor.getValue().getFirst().dutyStatus()).isNull();
    }

    @Test
    void execute_entryWithNoDriver_filteredOut() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        HosClocksForDriver noDriver = new HosClocksForDriver().currentDutyStatus(new CurrentDutyStatus());
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(noDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        tasklet.execute(null, null);

        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(List.of());
    }

    private SamsaraDriverRow driverRow(String id) {
        return new SamsaraDriverRow(
                id, "Jane Trucker", null, null, null, null, null, "active", null, null, null, null, null);
    }
}
