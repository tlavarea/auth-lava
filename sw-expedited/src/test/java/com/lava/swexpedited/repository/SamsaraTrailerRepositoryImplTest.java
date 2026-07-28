package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SamsaraTrailerRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private SamsaraTrailerRepository samsaraTrailerRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.samsaraTrailerRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.samsaraTrailerRepository.replaceAll(
                List.of(row("trailer-1", "5MC125315H5165489"), row("trailer-2", "1M9EU5327PT001234")));

        List<SamsaraTrailerRow> found = this.samsaraTrailerRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(SamsaraTrailerRow::id).containsExactlyInAnyOrder("trailer-1", "trailer-2");
        assertThat(found).allSatisfy(trailer -> assertThat(trailer.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.samsaraTrailerRepository.replaceAll(List.of(row("trailer-1", "5MC125315H5165489")));

        this.samsaraTrailerRepository.replaceAll(List.of(row("trailer-2", "1M9EU5327PT001234")));

        List<SamsaraTrailerRow> found = this.samsaraTrailerRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("trailer-2");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.samsaraTrailerRepository.replaceAll(List.of(row("trailer-1", "5MC125315H5165489")));

        this.samsaraTrailerRepository.replaceAll(List.of());

        assertThat(this.samsaraTrailerRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.samsaraTrailerRepository.findById("trailer-1")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithVinAndRawResponsePreserved() {
        this.samsaraTrailerRepository.replaceAll(List.of(row("trailer-1", "5MC125315H5165489")));

        assertThat(this.samsaraTrailerRepository.findById("trailer-1"))
                .isPresent()
                .get()
                .satisfies(trailer -> {
                    assertThat(trailer.vin()).isEqualTo("5MC125315H5165489");
                    assertThat(trailer.trailerSerialNumber()).isEqualTo("5MC125315H5165489");
                    assertThat(trailer.rawResponse()).isEqualTo("{\"id\":\"trailer-1\"}");
                });
    }

    private SamsaraTrailerRow row(String id, String vin) {
        return new SamsaraTrailerRow(id, vin, "1704", "34A1W4", vin, "{\"id\":\"" + id + "\"}", null);
    }
}
