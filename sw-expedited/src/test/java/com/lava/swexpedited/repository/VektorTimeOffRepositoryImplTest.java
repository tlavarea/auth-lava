package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.VektorTimeOffRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VektorTimeOffRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private VektorTimeOffRepository vektorTimeOffRepository;

    @Test
    void findByWindow_noRows_isEmpty() {
        assertThat(this.vektorTimeOffRepository.findByWindow(
                        LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0)))
                .isEmpty();
    }

    @Test
    void upsertAll_populatesTable() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1"), row("time-off-2", "truck-2")));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0));

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorTimeOffRow::id).containsExactlyInAnyOrder("time-off-1", "time-off-2");
        assertThat(found).allSatisfy(timeOff -> assertThat(timeOff.syncedAt()).isNotNull());
    }

    @Test
    void upsertAll_calledAgainWithDifferentId_keepsPreviousRows() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-2", "truck-2")));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0));
        assertThat(found).extracting(VektorTimeOffRow::id).containsExactlyInAnyOrder("time-off-1", "time-off-2");
    }

    @Test
    void upsertAll_calledAgainWithSameId_updatesRowInPlace() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        this.vektorTimeOffRepository.upsertAll(List.of(new VektorTimeOffRow(
                "time-off-1",
                "truck-1",
                "samsara-1",
                LocalDateTime.of(2026, 7, 17, 0, 0),
                LocalDateTime.of(2026, 7, 20, 0, 0),
                "Updated reason",
                "{}",
                null)));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().reason()).isEqualTo("Updated reason");
        assertThat(found.getFirst().matchedSamsaraDriverId()).isEqualTo("samsara-1");
    }

    @Test
    void upsertAll_emptyList_leavesTableUnchanged() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        this.vektorTimeOffRepository.upsertAll(List.of());

        assertThat(this.vektorTimeOffRepository.findByWindow(
                        LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0)))
                .hasSize(1);
    }

    @Test
    void findByWindow_timeOffOutsideWindow_isExcluded() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 8, 0, 0));

        assertThat(found).isEmpty();
    }

    @Test
    void findByWindow_timeOffOverlappingWindow_isIncluded() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 7, 16, 0, 0), LocalDateTime.of(2026, 7, 19, 0, 0));

        assertThat(found).extracting(VektorTimeOffRow::id).containsExactly("time-off-1");
    }

    @Test
    void upsertAll_persistsAllFieldsAndNullMatchedSamsaraDriverId() {
        this.vektorTimeOffRepository.upsertAll(List.of(row("time-off-1", "truck-1")));

        List<VektorTimeOffRow> found = this.vektorTimeOffRepository.findByWindow(
                LocalDateTime.of(2026, 7, 13, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0));

        assertThat(found).hasSize(1);
        VektorTimeOffRow timeOff = found.getFirst();
        assertThat(timeOff.truckId()).isEqualTo("truck-1");
        assertThat(timeOff.matchedSamsaraDriverId()).isNull();
        assertThat(timeOff.startAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 0, 0));
        assertThat(timeOff.endAt()).isEqualTo(LocalDateTime.of(2026, 7, 18, 0, 0));
        assertThat(timeOff.reason()).isEqualTo("Vacation");
        assertThat(timeOff.rawResponse()).isEqualTo("{}");
    }

    private VektorTimeOffRow row(String id, String truckId) {
        return new VektorTimeOffRow(
                id,
                truckId,
                null,
                LocalDateTime.of(2026, 7, 17, 0, 0),
                LocalDateTime.of(2026, 7, 18, 0, 0),
                "Vacation",
                "{}",
                null);
    }
}
