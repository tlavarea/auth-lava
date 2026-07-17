package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.SamsaraFleetClient;
import com.lava.swexpedited.samsara.DriverActivityEntry;
import com.lava.swexpedited.samsara.model.HosLogEntry;
import com.lava.swexpedited.samsara.model.HosLogLocation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SamsaraDriverActivityServiceImplTest {

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Test
    void findActivity_mapsEntriesAndSortsNewestFirst() {
        Instant since = Instant.parse("2026-07-15T12:00:00Z");
        HosLogEntry earlier = new HosLogEntry()
                .hosStatusType("onDuty")
                .logStartTime("2026-07-16T10:48:00Z")
                .logEndTime("2026-07-16T11:04:00Z")
                .remark("Pre-trip inspection");
        HosLogEntry later = new HosLogEntry()
                .hosStatusType("driving")
                .logStartTime("2026-07-16T11:04:00Z")
                .logEndTime(null)
                .logRecordedLocation(new HosLogLocation().latitude(27.9).longitude(-81.6));
        when(this.samsaraFleetClient.fetchDriverHosLogs(eq("41000123"), eq(since), any(Instant.class)))
                .thenReturn(List.of(earlier, later));

        SamsaraDriverActivityServiceImpl service = new SamsaraDriverActivityServiceImpl(this.samsaraFleetClient);

        List<DriverActivityEntry> activity = service.findActivity("41000123", since);

        assertThat(activity).hasSize(2);
        assertThat(activity.getFirst().dutyStatus()).isEqualTo("driving");
        assertThat(activity.getFirst().startTime()).isEqualTo(Instant.parse("2026-07-16T11:04:00Z"));
        assertThat(activity.getFirst().endTime()).isNull();
        assertThat(activity.getFirst().latitude()).isEqualByComparingTo("27.9");
        assertThat(activity.getFirst().longitude()).isEqualByComparingTo("-81.6");
        assertThat(activity.getLast().dutyStatus()).isEqualTo("onDuty");
        assertThat(activity.getLast().endTime()).isEqualTo(Instant.parse("2026-07-16T11:04:00Z"));
        assertThat(activity.getLast().remark()).isEqualTo("Pre-trip inspection");
        assertThat(activity.getLast().latitude()).isNull();
    }

    // Regression test for a bug where the offset was silently dropped instead of applied - a log timestamp with a
    // non-UTC offset must resolve to the same absolute instant as its UTC equivalent, not have its offset stripped
    // and its raw clock digits misread as if they were already UTC.
    @Test
    void findActivity_preservesNonUtcOffsetAsTheSameInstant() {
        Instant since = Instant.parse("2026-07-15T12:00:00Z");
        HosLogEntry entry = new HosLogEntry()
                .hosStatusType("driving")
                .logStartTime("2026-07-16T06:04:00-05:00")
                .logEndTime(null);
        when(this.samsaraFleetClient.fetchDriverHosLogs(eq("41000123"), eq(since), any(Instant.class)))
                .thenReturn(List.of(entry));

        SamsaraDriverActivityServiceImpl service = new SamsaraDriverActivityServiceImpl(this.samsaraFleetClient);

        List<DriverActivityEntry> activity = service.findActivity("41000123", since);

        assertThat(activity.getFirst().startTime()).isEqualTo(Instant.parse("2026-07-16T11:04:00Z"));
    }

    @Test
    void findActivity_noLogs_isEmpty() {
        when(this.samsaraFleetClient.fetchDriverHosLogs(eq("41000123"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        SamsaraDriverActivityServiceImpl service = new SamsaraDriverActivityServiceImpl(this.samsaraFleetClient);

        assertThat(service.findActivity("41000123", Instant.now().minusSeconds(3600)))
                .isEmpty();
    }
}
