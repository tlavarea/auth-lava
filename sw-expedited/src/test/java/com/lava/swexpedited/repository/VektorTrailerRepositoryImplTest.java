package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.VektorTrailerRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VektorTrailerRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private VektorTrailerRepository vektorTrailerRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.vektorTrailerRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.vektorTrailerRepository.replaceAll(
                List.of(row("trailer-1", "T231 - 53' SDL"), row("trailer-2", "U51620")));

        List<VektorTrailerRow> found = this.vektorTrailerRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorTrailerRow::id).containsExactlyInAnyOrder("trailer-1", "trailer-2");
        assertThat(found).allSatisfy(trailer -> assertThat(trailer.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.vektorTrailerRepository.replaceAll(List.of(row("trailer-1", "T231 - 53' SDL")));

        this.vektorTrailerRepository.replaceAll(List.of(row("trailer-2", "U51620")));

        List<VektorTrailerRow> found = this.vektorTrailerRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isEqualTo("trailer-2");
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.vektorTrailerRepository.replaceAll(List.of(row("trailer-1", "T231 - 53' SDL")));

        this.vektorTrailerRepository.replaceAll(List.of());

        assertThat(this.vektorTrailerRepository.findAll()).isEmpty();
    }

    @Test
    void findById_noRow_isEmpty() {
        assertThat(this.vektorTrailerRepository.findById("trailer-1")).isEmpty();
    }

    @Test
    void findById_matchingRow_returnsItWithRawResponsePreserved() {
        this.vektorTrailerRepository.replaceAll(List.of(row("trailer-1", "T231 - 53' SDL")));

        assertThat(this.vektorTrailerRepository.findById("trailer-1"))
                .isPresent()
                .get()
                .satisfies(trailer -> {
                    assertThat(trailer.label()).isEqualTo("T231 - 53' SDL");
                    assertThat(trailer.rawResponse()).isEqualTo("{\"id\":\"trailer-1\"}");
                });
    }

    private VektorTrailerRow row(String id, String label) {
        return new VektorTrailerRow(id, label, null, null, "{\"id\":\"" + id + "\"}", null);
    }
}
