package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraVehicleRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraVehicleRepository samsaraVehicleRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraVehicleRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraVehicleRepository.replaceAll(List.of(row("281474", "1XPBD49X7ND764317"), row("281475", "VIN2")));

        List<SamsaraVehicleRow> found = this.samsaraVehicleRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(SamsaraVehicleRow::id).containsExactlyInAnyOrder("281474", "281475");
        assertThat(found).allSatisfy(vehicle -> assertThat(vehicle.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraVehicleRepository.replaceAll(List.of(row("281474", "1XPBD49X7ND764317")));

        this.samsaraVehicleRepository.replaceAll(List.of(row("281475", "VIN2")));

        List<SamsaraVehicleRow> found = this.samsaraVehicleRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("281475");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraVehicleRepository.replaceAll(List.of(row("281474", "1XPBD49X7ND764317")));

        this.samsaraVehicleRepository.replaceAll(List.of());

        assertThat(this.samsaraVehicleRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.samsaraVehicleRepository.findById("281474")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithRawResponsePreserved() {
        this.samsaraVehicleRepository.replaceAll(List.of(row("281474", "1XPBD49X7ND764317")));

        assertThat(this.samsaraVehicleRepository.findById("281474"))
                .isPresent()
                .get()
                .satisfies(vehicle -> {
                    assertThat(vehicle.vin()).isEqualTo("1XPBD49X7ND764317");
                    assertThat(vehicle.rawResponse()).isEqualTo("{\"id\":\"281474\"}");
                });
    }

    private SamsaraVehicleRow row(String id, String vin) {
        return new SamsaraVehicleRow(
                id, vin, "2203", "PETERBILT", "579", "2022", "AN02697", "{\"id\":\"" + id + "\"}", null);
    }
}
