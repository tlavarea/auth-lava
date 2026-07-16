package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraDriverVehicleAssignmentRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraDriverRepository samsaraDriverRepository;

    @Autowired
    private SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraDriverVehicleAssignmentRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123"), driverRow("41000456")));

        this.samsaraDriverVehicleAssignmentRepository.replaceAll(
                List.of(assignmentRow("41000123", "281474"), assignmentRow("41000456", "281475")));

        List<SamsaraDriverVehicleAssignmentRow> found = this.samsaraDriverVehicleAssignmentRepository.findAll();
        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(SamsaraDriverVehicleAssignmentRow::driverId)
                .containsExactlyInAnyOrder("41000123", "41000456");
        assertThat(found).allSatisfy(row -> assertThat(row.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123"), driverRow("41000456")));
        this.samsaraDriverVehicleAssignmentRepository.replaceAll(List.of(assignmentRow("41000123", "281474")));

        this.samsaraDriverVehicleAssignmentRepository.replaceAll(List.of(assignmentRow("41000456", "281475")));

        List<SamsaraDriverVehicleAssignmentRow> found = this.samsaraDriverVehicleAssignmentRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().driverId()).isEqualTo("41000456");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123")));
        this.samsaraDriverVehicleAssignmentRepository.replaceAll(List.of(assignmentRow("41000123", "281474")));

        this.samsaraDriverVehicleAssignmentRepository.replaceAll(List.of());

        assertThat(this.samsaraDriverVehicleAssignmentRepository.findAll()).isEmpty();
    }

    @Test
    void findByDriverId_noRow_isEmpty() {
        assertThat(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .isEmpty();
    }

    @Test
    void findByDriverId_matchingRow_returnsIt() {
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123"), driverRow("41000456")));
        this.samsaraDriverVehicleAssignmentRepository.replaceAll(
                List.of(assignmentRow("41000123", "281474"), assignmentRow("41000456", "281475")));

        assertThat(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .isPresent()
                .get()
                .extracting(SamsaraDriverVehicleAssignmentRow::vehicleId)
                .isEqualTo("281474");
    }

    @Test
    void driverReplaceAll_omittingPreviouslyInsertedDriver_cascadesDeleteToAssignment() {
        // SamsaraDriverRepositoryImpl.replaceAll deletes every row in samsara_driver (not just the omitted one)
        // before re-inserting, so the FK cascade clears every assignment row - both 41000123's (correctly omitted
        // from the new roster) and 41000456's (still present in the new roster, but its old assignment row is
        // cascade-deleted along with everything else; a fresh assignment for it would only reappear once
        // SamsaraDriverVehicleAssignmentRepository.replaceAll runs again, as SamsaraDriverSyncTasklet does).
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123"), driverRow("41000456")));
        this.samsaraDriverVehicleAssignmentRepository.replaceAll(
                List.of(assignmentRow("41000123", "281474"), assignmentRow("41000456", "281475")));

        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000456")));

        assertThat(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000123"))
                .isEmpty();
        assertThat(this.samsaraDriverVehicleAssignmentRepository.findByDriverId("41000456"))
                .isEmpty();
    }

    @Test
    void driverReplaceAll_emptyList_cascadesDeleteToAllAssignments() {
        this.samsaraDriverRepository.replaceAll(List.of(driverRow("41000123")));
        this.samsaraDriverVehicleAssignmentRepository.replaceAll(List.of(assignmentRow("41000123", "281474")));

        this.samsaraDriverRepository.replaceAll(List.of());

        assertThat(this.samsaraDriverVehicleAssignmentRepository.findAll()).isEmpty();
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
                null);
    }

    private SamsaraDriverVehicleAssignmentRow assignmentRow(String driverId, String vehicleId) {
        return new SamsaraDriverVehicleAssignmentRow(
                driverId,
                vehicleId,
                "Truck " + vehicleId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(1),
                null);
    }
}
