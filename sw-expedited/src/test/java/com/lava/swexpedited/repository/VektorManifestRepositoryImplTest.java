package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.vektor.StopType;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorManifestStartingPosition;
import com.lava.swexpedited.vektor.VektorManifestStop;
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
    void upsertAll_populatesTable() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000587L, "Kelly Dunn"), row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(VektorManifestRow::manifestNumber).containsExactlyInAnyOrder(1000587L, 1000589L);
        assertThat(found).allSatisfy(manifest -> assertThat(manifest.syncedAt()).isNotNull());
    }

    @Test
    void upsertAll_calledAgainWithDifferentManifestNumber_keepsPreviousRows() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000587L, "Kelly Dunn")));

        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findAll();
        assertThat(found).extracting(VektorManifestRow::manifestNumber).containsExactlyInAnyOrder(1000587L, 1000589L);
    }

    @Test
    void upsertAll_calledAgainWithSameManifestNumber_updatesRowInPlace() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare")));

        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare", "manifest_completed")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().status()).isEqualTo("manifest_completed");
    }

    @Test
    void upsertAll_emptyList_leavesTableUnchanged() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000587L, "Kelly Dunn")));

        this.vektorManifestRepository.upsertAll(List.of());

        assertThat(this.vektorManifestRepository.findAll()).hasSize(1);
    }

    @Test
    void findByManifestNumber_noRow_isEmpty() {
        assertThat(this.vektorManifestRepository.findByManifestNumber(1000589L)).isEmpty();
    }

    @Test
    void findByManifestNumber_matchingRow_returnsItWithAllFields() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare")));

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
                    assertThat(manifest.startingPosition().address()).isEqualTo("Prior stop, GA");
                    assertThat(manifest.startingPosition().latitude()).isEqualByComparingTo(new BigDecimal("31.19"));
                    assertThat(manifest.stops()).hasSize(1);
                    VektorManifestStop stop = manifest.stops().getFirst();
                    assertThat(stop.sequenceNumber()).isEqualTo(1);
                    assertThat(stop.stopType()).isEqualTo(StopType.PICKUP);
                    assertThat(stop.siteName()).isEqualTo("Dealer Warehouse");
                    assertThat(stop.contactPhone()).isEqualTo("+19127483999");
                    assertThat(stop.arrivedAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 6, 14));
                });
    }

    @Test
    void upsertAll_manifestWithNoStartingPosition_persistsNullStartingPosition() {
        this.vektorManifestRepository.upsertAll(List.of(new VektorManifestRow(
                1000589L,
                "manifest-uuid-1000589",
                "driver-uuid-1000589",
                "Warren Ruawhare",
                null,
                "manifest_in_progress",
                "Bessemer, AL",
                "Litchfield Park, AZ",
                new BigDecimal("33.528326"),
                new BigDecimal("-112.403152"),
                LocalDateTime.of(2026, 7, 17, 8, 0, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0, 0),
                "SwX-1000589",
                List.of(),
                null,
                "{}",
                null)));

        assertThat(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .isPresent()
                .get()
                .satisfies(manifest -> {
                    assertThat(manifest.startingPosition()).isNull();
                    assertThat(manifest.stops()).isEmpty();
                });
    }

    @Test
    void findByAppointmentWindow_manifestOutsideWindow_isExcluded() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findByAppointmentWindow(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 8, 0, 0));

        assertThat(found).isEmpty();
    }

    @Test
    void findByAppointmentWindow_manifestOverlappingWindow_isIncluded() {
        this.vektorManifestRepository.upsertAll(List.of(row(1000589L, "Warren Ruawhare")));

        List<VektorManifestRow> found = this.vektorManifestRepository.findByAppointmentWindow(
                LocalDateTime.of(2026, 7, 19, 0, 0), LocalDateTime.of(2026, 7, 26, 0, 0));

        assertThat(found).extracting(VektorManifestRow::manifestNumber).containsExactly(1000589L);
    }

    @Test
    void findByAppointmentWindow_manifestMissingEta_isExcluded() {
        this.vektorManifestRepository.upsertAll(List.of(new VektorManifestRow(
                1000589L,
                "manifest-uuid-1000589",
                "driver-uuid-1000589",
                "Warren Ruawhare",
                null,
                "manifest_in_progress",
                "Bessemer, AL",
                "Litchfield Park, AZ",
                new BigDecimal("33.528326"),
                new BigDecimal("-112.403152"),
                LocalDateTime.of(2026, 7, 17, 8, 0, 0),
                null,
                "SwX-1000589",
                List.of(),
                null,
                "{}",
                null)));

        List<VektorManifestRow> found = this.vektorManifestRepository.findByAppointmentWindow(
                LocalDateTime.of(2026, 7, 17, 0, 0), LocalDateTime.of(2026, 7, 24, 0, 0));

        assertThat(found).isEmpty();
    }

    private VektorManifestRow row(long manifestNumber, String driverName) {
        return row(manifestNumber, driverName, "manifest_in_progress");
    }

    private VektorManifestRow row(long manifestNumber, String driverName, String status) {
        return new VektorManifestRow(
                manifestNumber,
                "manifest-uuid-" + manifestNumber,
                "driver-uuid-" + manifestNumber,
                driverName,
                null,
                status,
                "Bessemer, AL",
                "Litchfield Park, AZ",
                new BigDecimal("33.528326"),
                new BigDecimal("-112.403152"),
                LocalDateTime.of(2026, 7, 17, 8, 0, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0, 0),
                "SwX-1000589",
                List.of(new VektorManifestStop(
                        1,
                        StopType.PICKUP,
                        "Dealer Warehouse",
                        "122 Norwest Ct, Savannah, GA 31407",
                        new BigDecimal("32.167947"),
                        new BigDecimal("-81.236379"),
                        "EDT",
                        LocalDateTime.of(2026, 7, 17, 9, 30, 0),
                        LocalDateTime.of(2026, 7, 17, 10, 0, 0),
                        LocalDateTime.of(2026, 7, 17, 10, 6, 14),
                        LocalDateTime.of(2026, 7, 17, 10, 6, 16),
                        LocalDateTime.of(2026, 7, 17, 11, 55, 54),
                        "CO 01660967",
                        "Check in no more than 15 minutes before your loading time.",
                        "+19127483999",
                        new BigDecimal("83.00"),
                        new BigDecimal("13.00"),
                        new BigDecimal("406717"))),
                new VektorManifestStartingPosition(
                        "Prior stop, GA",
                        new BigDecimal("31.19"),
                        new BigDecimal("-81.47"),
                        "Last stop of previous manifest",
                        new BigDecimal("74.00"),
                        new BigDecimal("174.00"),
                        new BigDecimal("406543")),
                "{}",
                null);
    }
}
