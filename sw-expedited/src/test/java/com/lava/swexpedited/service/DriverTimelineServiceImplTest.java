package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.samsara.DriverTimelineRow;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverTimelineServiceImplTest {

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Test
    void findAll_driverWithMatchedManifest_includesManifestFields() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverDutyStatusRepository.findAll())
                .thenReturn(List.of(dutyStatusRow("41000123", "driving")));
        when(this.vektorManifestRepository.findAll())
                .thenReturn(List.of(manifestRow(1000589L, "41000123", LocalDateTime.of(2026, 7, 17, 8, 0))));
        DriverTimelineServiceImpl service = new DriverTimelineServiceImpl(
                this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository, this.vektorManifestRepository);

        List<DriverTimelineRow> result = service.findAll();

        assertThat(result).hasSize(1);
        DriverTimelineRow row = result.getFirst();
        assertThat(row.driverId()).isEqualTo("41000123");
        assertThat(row.dutyStatus()).isEqualTo("driving");
        assertThat(row.manifestStatus()).isEqualTo("manifest_in_progress");
        assertThat(row.pickupAppointmentStart()).isEqualTo(LocalDateTime.of(2026, 7, 17, 8, 0));
        assertThat(row.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0));
        assertThat(row.origin()).isEqualTo("4251 Turin Dr, Bessemer, AL 35020");
        assertThat(row.destination()).isEqualTo("6390 N Alsup Rd, Litchfield Park, AZ 85340");
        assertThat(row.loadReference()).isEqualTo("SwX-1000589");
    }

    @Test
    void findAll_driverWithNoMatchedManifest_manifestFieldsAreNull() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverDutyStatusRepository.findAll()).thenReturn(List.of());
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of());
        DriverTimelineServiceImpl service = new DriverTimelineServiceImpl(
                this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository, this.vektorManifestRepository);

        List<DriverTimelineRow> result = service.findAll();

        assertThat(result).hasSize(1);
        DriverTimelineRow row = result.getFirst();
        assertThat(row.dutyStatus()).isNull();
        assertThat(row.manifestStatus()).isNull();
        assertThat(row.pickupAppointmentStart()).isNull();
        assertThat(row.eta()).isNull();
        assertThat(row.origin()).isNull();
    }

    @Test
    void findAll_unmatchedManifest_isIgnored() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverDutyStatusRepository.findAll()).thenReturn(List.of());
        when(this.vektorManifestRepository.findAll())
                .thenReturn(List.of(manifestRow(1000589L, null, LocalDateTime.of(2026, 7, 17, 8, 0))));
        DriverTimelineServiceImpl service = new DriverTimelineServiceImpl(
                this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository, this.vektorManifestRepository);

        List<DriverTimelineRow> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().manifestStatus()).isNull();
    }

    @Test
    void findAll_multipleMatchedManifestsForSameDriver_soonestPickupWins() {
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(driverRow("41000123")));
        when(this.samsaraDriverDutyStatusRepository.findAll()).thenReturn(List.of());
        when(this.vektorManifestRepository.findAll())
                .thenReturn(List.of(
                        manifestRow(1000589L, "41000123", LocalDateTime.of(2026, 7, 18, 8, 0)),
                        manifestRow(1000590L, "41000123", LocalDateTime.of(2026, 7, 17, 8, 0))));
        DriverTimelineServiceImpl service = new DriverTimelineServiceImpl(
                this.samsaraDriverRepository, this.samsaraDriverDutyStatusRepository, this.vektorManifestRepository);

        List<DriverTimelineRow> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().pickupAppointmentStart()).isEqualTo(LocalDateTime.of(2026, 7, 17, 8, 0));
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

    private SamsaraDriverDutyStatusRow dutyStatusRow(String driverId, String dutyStatus) {
        return new SamsaraDriverDutyStatusRow(
                driverId,
                dutyStatus,
                2_000L,
                3_000L,
                4_000L,
                1_000L,
                LocalDateTime.now().minusMinutes(103),
                null);
    }

    private VektorManifestRow manifestRow(
            long manifestNumber, String matchedSamsaraDriverId, LocalDateTime pickupAppointmentStart) {
        return new VektorManifestRow(
                manifestNumber,
                "71da0ba8-865b-4c1a-8ad1-b95a4d2b8398",
                "b4a58cf3-150c-4ab8-9f9a-31a03da29bc2",
                "Warren Ruawhare",
                matchedSamsaraDriverId,
                "manifest_in_progress",
                "4251 Turin Dr, Bessemer, AL 35020",
                "6390 N Alsup Rd, Litchfield Park, AZ 85340",
                null,
                null,
                pickupAppointmentStart,
                LocalDateTime.of(2026, 7, 20, 10, 0),
                "SwX-" + manifestNumber,
                "{}",
                null);
    }
}
