package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class VektorManifestRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private VektorManifestRepository vektorManifestRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.vektorManifestRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.vektorManifestRepository.replaceAll(
                List.of(row(1000587L, "Kelly Dunn"), row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorManifestRow::manifestNumber).containsExactlyInAnyOrder(1000587L, 1000589L);
        assertThat(found).allSatisfy(manifest -> assertThat(manifest.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.vektorManifestRepository.replaceAll(List.of(row(1000587L, "Kelly Dunn")));

        this.vektorManifestRepository.replaceAll(List.of(row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().manifestNumber()).isEqualTo(1000589L);
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.vektorManifestRepository.replaceAll(List.of(row(1000587L, "Kelly Dunn")));

        this.vektorManifestRepository.replaceAll(List.of());

        assertThat(this.vektorManifestRepository.findAll()).isEmpty();
    }

    @Test
    void findByManifestNumber_noRow_isEmpty() {
        assertThat(this.vektorManifestRepository.findByManifestNumber(1000589L)).isEmpty();
    }

    @Test
    void findByManifestNumber_matchingRow_returnsItWithAllFields() {
        this.vektorManifestRepository.replaceAll(List.of(row(1000589L, "Warren Ruawhare")));

        assertThat(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .isPresent()
                .get()
                .satisfies(manifest -> {
                    assertThat(manifest.manifestId()).isEqualTo("manifest-uuid-1000589");
                    assertThat(manifest.driverName()).isEqualTo("Warren Ruawhare");
                    assertThat(manifest.status()).isEqualTo("manifest_in_progress");
                    assertThat(manifest.origin()).isEqualTo("Bessemer, AL");
                    assertThat(manifest.destination()).isEqualTo("Litchfield Park, AZ");
                    assertThat(manifest.destinationLatitude()).isEqualByComparingTo(new BigDecimal("33.528326"));
                    assertThat(manifest.destinationLongitude()).isEqualByComparingTo(new BigDecimal("-112.403152"));
                    assertThat(manifest.pickupAppointmentStart()).isEqualTo(LocalDateTime.of(2026, 7, 17, 8, 0, 0));
                    assertThat(manifest.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0, 0));
                    assertThat(manifest.loadReference()).isEqualTo("SwX-1000589");
                    assertThat(manifest.rawResponse()).isEqualTo("{}");
                });
    }

    private VektorManifestRow row(long manifestNumber, String driverName) {
        return new VektorManifestRow(
                manifestNumber,
                "manifest-uuid-" + manifestNumber,
                "driver-uuid-" + manifestNumber,
                driverName,
                null,
                "manifest_in_progress",
                "Bessemer, AL",
                "Litchfield Park, AZ",
                new BigDecimal("33.528326"),
                new BigDecimal("-112.403152"),
                LocalDateTime.of(2026, 7, 17, 8, 0, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0, 0),
                "SwX-1000589",
                "{}",
                null);
    }
}
