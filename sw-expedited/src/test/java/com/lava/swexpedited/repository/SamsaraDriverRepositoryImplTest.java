package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraDriverRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraDriverRepository samsaraDriverRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraDriverRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraDriverRepository.replaceAll(
                List.of(row("41000123", "Jane Trucker"), row("41000456", "John Hauler")));

        List<SamsaraDriverRow> found = this.samsaraDriverRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(SamsaraDriverRow::id).containsExactlyInAnyOrder("41000123", "41000456");
        assertThat(found).allSatisfy(driver -> assertThat(driver.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraDriverRepository.replaceAll(List.of(row("41000123", "Jane Trucker")));

        this.samsaraDriverRepository.replaceAll(List.of(row("41000456", "John Hauler")));

        List<SamsaraDriverRow> found = this.samsaraDriverRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("41000456");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraDriverRepository.replaceAll(List.of(row("41000123", "Jane Trucker")));

        this.samsaraDriverRepository.replaceAll(List.of());

        assertThat(this.samsaraDriverRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.samsaraDriverRepository.findById("41000123")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithRawResponsePreserved() {
        this.samsaraDriverRepository.replaceAll(
                List.of(row("41000123", "Jane Trucker"), row("41000456", "John Hauler")));

        assertThat(this.samsaraDriverRepository.findById("41000123"))
                .isPresent()
                .get()
                .satisfies(driver -> {
                    assertThat(driver.name()).isEqualTo("Jane Trucker");
                    assertThat(driver.rawResponse()).isEqualTo("{\"id\":\"41000123\",\"name\":\"Jane Trucker\"}");
                });
    }

    private SamsaraDriverRow row(String id, String name) {
        return new SamsaraDriverRow(
                id,
                name,
                "jtrucker",
                "jane.trucker@example.com",
                "555-0100",
                "D1234567",
                "TX",
                "active",
                "expedited,reefer",
                LocalDateTime.now().minusMonths(6),
                LocalDateTime.now().minusDays(1),
                "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}",
                null);
    }
}
