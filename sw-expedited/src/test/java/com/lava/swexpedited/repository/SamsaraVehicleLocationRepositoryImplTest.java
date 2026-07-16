package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraVehicleLocationRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraVehicleLocationRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraVehicleLocationRepository.replaceAll(List.of(row("281474", "Truck 12"), row("281475", "Truck 13")));

        List<SamsaraVehicleLocationRow> found = this.samsaraVehicleLocationRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(SamsaraVehicleLocationRow::vehicleId)
                .containsExactlyInAnyOrder("281474", "281475");
        assertThat(found).allSatisfy(location -> assertThat(location.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraVehicleLocationRepository.replaceAll(List.of(row("281474", "Truck 12")));

        this.samsaraVehicleLocationRepository.replaceAll(List.of(row("281475", "Truck 13")));

        List<SamsaraVehicleLocationRow> found = this.samsaraVehicleLocationRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().vehicleId()).isEqualTo("281475");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraVehicleLocationRepository.replaceAll(List.of(row("281474", "Truck 12")));

        this.samsaraVehicleLocationRepository.replaceAll(List.of());

        assertThat(this.samsaraVehicleLocationRepository.findAll()).isEmpty();
    }

    @Test
    void findByVehicleId_noRow_isEmpty() {
        assertThat(this.samsaraVehicleLocationRepository.findByVehicleId("281474"))
                .isEmpty();
    }

    @Test
    void findByVehicleId_matchingRow_returnsIt() {
        this.samsaraVehicleLocationRepository.replaceAll(List.of(row("281474", "Truck 12"), row("281475", "Truck 13")));

        assertThat(this.samsaraVehicleLocationRepository.findByVehicleId("281474"))
                .isPresent()
                .get()
                .extracting(SamsaraVehicleLocationRow::vehicleName)
                .isEqualTo("Truck 12");
    }

    private SamsaraVehicleLocationRow row(String vehicleId, String vehicleName) {
        return new SamsaraVehicleLocationRow(
                vehicleId,
                vehicleName,
                new BigDecimal("32.735000"),
                new BigDecimal("-97.108000"),
                new BigDecimal("180.50"),
                new BigDecimal("62.30"),
                LocalDateTime.now().minusMinutes(1),
                "Fort Worth, TX",
                null);
    }
}
