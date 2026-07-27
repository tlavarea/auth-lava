package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VektorTruckRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private VektorTruckRepository vektorTruckRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.vektorTruckRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.vektorTruckRepository.replaceAll(List.of(row("truck-1", "2401", null), row("truck-2", "2402", null)));

        List<VektorTruckRow> found = this.vektorTruckRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorTruckRow::id).containsExactlyInAnyOrder("truck-1", "truck-2");
        assertThat(found).allSatisfy(truck -> assertThat(truck.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.vektorTruckRepository.replaceAll(List.of(row("truck-1", "2401", null)));

        this.vektorTruckRepository.replaceAll(List.of(row("truck-2", "2402", null)));

        List<VektorTruckRow> found = this.vektorTruckRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("truck-2");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.vektorTruckRepository.replaceAll(List.of(row("truck-1", "2401", null)));

        this.vektorTruckRepository.replaceAll(List.of());

        assertThat(this.vektorTruckRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.vektorTruckRepository.findById("truck-1")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithRawResponsePreserved() {
        this.vektorTruckRepository.replaceAll(List.of(row("truck-1", "2401", "driver-1")));

        assertThat(this.vektorTruckRepository.findById("truck-1"))
                .isPresent()
                .get()
                .satisfies(truck -> {
                    assertThat(truck.truckNumber()).isEqualTo("2401");
                    assertThat(truck.currentDriverId()).isEqualTo("driver-1");
                    assertThat(truck.rawResponse()).isEqualTo("{\"id\":\"truck-1\"}");
                });
    }

    @Test
    void findCurrentDriverIdByTruckId_onlyReturnsTrucksWithADriverAssigned() {
        this.vektorTruckRepository.replaceAll(
                List.of(row("truck-1", "2401", "driver-1"), row("truck-2", "2402", null)));

        Map<String, String> currentDrivers = this.vektorTruckRepository.findCurrentDriverIdByTruckId();

        assertThat(currentDrivers).containsExactly(Map.entry("truck-1", "driver-1"));
    }

    @Test
    void findCurrentDriverIdByTruckId_noTrucks_isEmptyMap() {
        assertThat(this.vektorTruckRepository.findCurrentDriverIdByTruckId()).isEmpty();
    }

    private VektorTruckRow row(String id, String truckNumber, String currentDriverId) {
        return new VektorTruckRow(
                id,
                truckNumber,
                null,
                null,
                null,
                null,
                null,
                null,
                currentDriverId,
                "{\"id\":\"" + id + "\"}",
                null,
                null);
    }
}
