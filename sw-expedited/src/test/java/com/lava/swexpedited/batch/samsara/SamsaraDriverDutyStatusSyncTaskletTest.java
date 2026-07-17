package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.model.CurrentDutyStatus;
import com.lava.swexpedited.samsara.model.DriverTinyResponse;
import com.lava.swexpedited.samsara.model.HosBreak;
import com.lava.swexpedited.samsara.model.HosClocks;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import com.lava.swexpedited.samsara.model.HosCycle;
import com.lava.swexpedited.samsara.model.HosDrive;
import com.lava.swexpedited.samsara.model.HosShift;
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
class SamsaraDriverDutyStatusSyncTaskletTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    @Test
    void execute_scopesFetchToSyncedRosterAndMapsDutyStatusesAndClocks() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType("driving"))
                .clocks(new HosClocks()
                        ._break(new HosBreak().timeUntilBreakDurationMs(1_000L))
                        .drive(new HosDrive().driveRemainingDurationMs(2_000L))
                        .shift(new HosShift().shiftRemainingDurationMs(3_000L))
                        .cycle(new HosCycle().cycleRemainingDurationMs(4_000L)));
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
        assertThat(row.timeUntilBreakDurationMs()).isEqualTo(1_000L);
        assertThat(row.driveRemainingDurationMs()).isEqualTo(2_000L);
        assertThat(row.shiftRemainingDurationMs()).isEqualTo(3_000L);
        assertThat(row.cycleRemainingDurationMs()).isEqualTo(4_000L);
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

    @Test
    void execute_noPreviousRow_setsDutyStatusSinceToNow() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverDutyStatusRepository.findAll()).thenReturn(List.of());
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType("driving"));
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(hosClocksForDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        LocalDateTime before = LocalDateTime.now();
        tasklet.execute(null, null);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<List<SamsaraDriverDutyStatusRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(captor.capture());
        LocalDateTime dutyStatusSince = captor.getValue().getFirst().dutyStatusSince();
        assertThat(dutyStatusSince).isNotNull().isBetween(before, after);
    }

    @Test
    void execute_unchangedDutyStatus_carriesForwardPreviousDutyStatusSince() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        LocalDateTime previousSince = LocalDateTime.now().minusHours(2);
        when(this.samsaraDriverDutyStatusRepository.findAll())
                .thenReturn(List.of(new SamsaraDriverDutyStatusRow(
                        "41000123", "driving", null, null, null, null, previousSince, null)));
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType("driving"));
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(hosClocksForDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        tasklet.execute(null, null);

        ArgumentCaptor<List<SamsaraDriverDutyStatusRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(captor.capture());
        assertThat(captor.getValue().getFirst().dutyStatusSince()).isEqualTo(previousSince);
    }

    @Test
    void execute_changedDutyStatus_resetsDutyStatusSinceToNow() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        LocalDateTime previousSince = LocalDateTime.now().minusHours(2);
        when(this.samsaraDriverDutyStatusRepository.findAll())
                .thenReturn(List.of(new SamsaraDriverDutyStatusRow(
                        "41000123", "onDuty", null, null, null, null, previousSince, null)));
        HosClocksForDriver hosClocksForDriver = new HosClocksForDriver()
                .driver(new DriverTinyResponse().id("41000123").name("Jane Trucker"))
                .currentDutyStatus(new CurrentDutyStatus().hosStatusType("driving"));
        when(this.samsaraFleetClient.fetchDriverDutyStatuses(List.of("41000123")))
                .thenReturn(List.of(hosClocksForDriver));

        SamsaraDriverDutyStatusSyncTasklet tasklet = new SamsaraDriverDutyStatusSyncTasklet(
                this.samsaraFleetClient, this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository);

        LocalDateTime before = LocalDateTime.now();
        tasklet.execute(null, null);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<List<SamsaraDriverDutyStatusRow>> captor = ArgumentCaptor.captor();
        Mockito.verify(this.samsaraDriverDutyStatusRepository).replaceAll(captor.capture());
        LocalDateTime dutyStatusSince = captor.getValue().getFirst().dutyStatusSince();
        assertThat(dutyStatusSince).isNotEqualTo(previousSince).isBetween(before, after);
    }

    private SamsaraDriverRow driverRow(String id) {
        return new SamsaraDriverRow(
                id, "Jane Trucker", null, null, null, null, null, "active", null, null, null, null, null);
    }
}
