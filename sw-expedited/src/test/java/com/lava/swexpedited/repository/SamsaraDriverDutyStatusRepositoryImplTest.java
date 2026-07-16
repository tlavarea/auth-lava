package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraDriverDutyStatusRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraDriverDutyStatusRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraDriverDutyStatusRepository.replaceAll(
                List.of(row("41000123", "driving"), row("41000456", "onDuty")));

        List<SamsaraDriverDutyStatusRow> found = this.samsaraDriverDutyStatusRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(SamsaraDriverDutyStatusRow::driverId)
                .containsExactlyInAnyOrder("41000123", "41000456");
        assertThat(found)
                .allSatisfy(dutyStatus -> assertThat(dutyStatus.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraDriverDutyStatusRepository.replaceAll(List.of(row("41000123", "driving")));

        this.samsaraDriverDutyStatusRepository.replaceAll(List.of(row("41000456", "onDuty")));

        List<SamsaraDriverDutyStatusRow> found = this.samsaraDriverDutyStatusRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().driverId()).isEqualTo("41000456");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraDriverDutyStatusRepository.replaceAll(List.of(row("41000123", "driving")));

        this.samsaraDriverDutyStatusRepository.replaceAll(List.of());

        assertThat(this.samsaraDriverDutyStatusRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_nullDutyStatus_isStored() {
        this.samsaraDriverDutyStatusRepository.replaceAll(List.of(row("41000123", null)));

        assertThat(this.samsaraDriverDutyStatusRepository.findByDriverId("41000123"))
                .isPresent()
                .get()
                .extracting(SamsaraDriverDutyStatusRow::dutyStatus)
                .isNull();
    }

    @Test
    void findByDriverId_noRow_isEmpty() {
        assertThat(this.samsaraDriverDutyStatusRepository.findByDriverId("41000123"))
                .isEmpty();
    }

    @Test
    void findByDriverId_matchingRow_returnsIt() {
        this.samsaraDriverDutyStatusRepository.replaceAll(
                List.of(row("41000123", "driving"), row("41000456", "onDuty")));

        assertThat(this.samsaraDriverDutyStatusRepository.findByDriverId("41000123"))
                .isPresent()
                .get()
                .extracting(SamsaraDriverDutyStatusRow::dutyStatus)
                .isEqualTo("driving");
    }

    @Test
    void replaceAll_storesClockFieldsAndDutyStatusSince() {
        LocalDateTime dutyStatusSince = LocalDateTime.now().minusMinutes(103).withNano(0);
        this.samsaraDriverDutyStatusRepository.replaceAll(List.of(new SamsaraDriverDutyStatusRow(
                "41000123", "driving", 1_000L, 2_000L, 3_000L, 4_000L, dutyStatusSince, null)));

        SamsaraDriverDutyStatusRow found = this.samsaraDriverDutyStatusRepository
                .findByDriverId("41000123")
                .orElseThrow();
        assertThat(found.driveRemainingDurationMs()).isEqualTo(1_000L);
        assertThat(found.shiftRemainingDurationMs()).isEqualTo(2_000L);
        assertThat(found.cycleRemainingDurationMs()).isEqualTo(3_000L);
        assertThat(found.timeUntilBreakDurationMs()).isEqualTo(4_000L);
        assertThat(found.dutyStatusSince()).isEqualTo(dutyStatusSince);
    }

    private SamsaraDriverDutyStatusRow row(String driverId, String dutyStatus) {
        return new SamsaraDriverDutyStatusRow(driverId, dutyStatus, null, null, null, null, null, null);
    }
}
