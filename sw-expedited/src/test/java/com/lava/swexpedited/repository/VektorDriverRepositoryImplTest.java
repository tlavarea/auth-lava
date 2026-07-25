package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.VektorDriverRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VektorDriverRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private VektorDriverRepository vektorDriverRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.vektorDriverRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.vektorDriverRepository.replaceAll(
                List.of(row("driver-1", "Jane Trucker"), row("driver-2", "John Hauler")));

        List<VektorDriverRow> found = this.vektorDriverRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorDriverRow::id).containsExactlyInAnyOrder("driver-1", "driver-2");
        assertThat(found).allSatisfy(driver -> assertThat(driver.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.vektorDriverRepository.replaceAll(List.of(row("driver-1", "Jane Trucker")));

        this.vektorDriverRepository.replaceAll(List.of(row("driver-2", "John Hauler")));

        List<VektorDriverRow> found = this.vektorDriverRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("driver-2");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.vektorDriverRepository.replaceAll(List.of(row("driver-1", "Jane Trucker")));

        this.vektorDriverRepository.replaceAll(List.of());

        assertThat(this.vektorDriverRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.vektorDriverRepository.findById("driver-1")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithRawResponsePreserved() {
        this.vektorDriverRepository.replaceAll(List.of(row("driver-1", "Jane Trucker")));

        assertThat(this.vektorDriverRepository.findById("driver-1"))
                .isPresent()
                .get()
                .satisfies(driver -> {
                    assertThat(driver.fullName()).isEqualTo("Jane Trucker");
                    assertThat(driver.rawResponse()).isEqualTo("{\"id\":\"driver-1\"}");
                });
    }

    @Test
    void findMatchedSamsaraDriverIdById_onlyReturnsNonNullMatches() {
        this.vektorDriverRepository.replaceAll(
                List.of(matchedRow("driver-1", "samsara-1"), matchedRow("driver-2", null)));

        Map<String, String> matched = this.vektorDriverRepository.findMatchedSamsaraDriverIdById();

        assertThat(matched).containsExactly(Map.entry("driver-1", "samsara-1"));
    }

    @Test
    void findMatchedSamsaraDriverIdById_noMatchedRows_isEmptyMap() {
        this.vektorDriverRepository.replaceAll(List.of(matchedRow("driver-1", null)));

        assertThat(this.vektorDriverRepository.findMatchedSamsaraDriverIdById()).isEmpty();
    }

    private VektorDriverRow row(String id, String fullName) {
        return new VektorDriverRow(id, null, fullName, null, null, null, "{\"id\":\"" + id + "\"}", null);
    }

    private VektorDriverRow matchedRow(String id, String matchedSamsaraDriverId) {
        return new VektorDriverRow(
                id, null, "Jane Trucker", null, null, matchedSamsaraDriverId, "{\"id\":\"" + id + "\"}", null);
    }
}
