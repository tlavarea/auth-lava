package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ShipmentDetailRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private ShipmentListingRepository shipmentListingRepository;

    @Autowired
    private ShipmentDetailRepository shipmentDetailRepository;

    @Test
    void findByOfferId_noRow_isEmpty() {
        assertThat(this.shipmentDetailRepository.findByOfferId(1284311010L)).isEmpty();
    }

    @Test
    void insertAll_thenFindByOfferId_returnsStoredDetail() {
        this.shipmentListingRepository.replaceAll(List.of(listingRow(1284311010L)));

        this.shipmentDetailRepository.insertAll(List.of(detailRow(1284311010L)));

        Optional<ShipmentDetailRow> found = this.shipmentDetailRepository.findByOfferId(1284311010L);
        assertThat(found).isPresent();
        assertThat(found.get().totalAmount()).isEqualByComparingTo(new BigDecimal("1416.00"));
        assertThat(found.get().scac()).isEqualTo("SWJJ");
        assertThat(found.get().rawResponse()).isEqualTo("{\"bid\":{\"totalAmount\":1416}}");
        assertThat(found.get().syncedAt()).isNotNull();
    }

    @Test
    void insertAll_emptyList_doesNothing() {
        this.shipmentListingRepository.replaceAll(List.of(listingRow(1284311010L)));

        this.shipmentDetailRepository.insertAll(List.of());

        assertThat(this.shipmentDetailRepository.findByOfferId(1284311010L)).isEmpty();
    }

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.shipmentDetailRepository.findAll()).isEmpty();
    }

    @Test
    void findAll_returnsEveryStoredDetailRow() {
        this.shipmentListingRepository.replaceAll(List.of(listingRow(1284311010L), listingRow(1284314723L)));

        this.shipmentDetailRepository.insertAll(List.of(detailRow(1284311010L), detailRow(1284314723L)));

        assertThat(this.shipmentDetailRepository.findAll())
                .extracting(ShipmentDetailRow::offerId)
                .containsExactlyInAnyOrder(1284311010L, 1284314723L);
    }

    @Test
    void listingReplaceAll_cascadesDeleteToShipmentDetail() {
        this.shipmentListingRepository.replaceAll(List.of(listingRow(1284311010L)));
        this.shipmentDetailRepository.insertAll(List.of(detailRow(1284311010L)));

        this.shipmentListingRepository.replaceAll(List.of(listingRow(1284314723L)));

        assertThat(this.shipmentDetailRepository.findByOfferId(1284311010L)).isEmpty();
    }

    private ShipmentListingRow listingRow(long offerId) {
        return new ShipmentListingRow(
                offerId,
                "Open",
                LocalDateTime.now().plusDays(1),
                "KLFV160850003",
                "FAK",
                "36",
                "KLFV",
                "774900240, KIRTLAND AFB,NM",
                "773466240, CANNON AFB,NM",
                "AF2",
                1,
                0,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(10),
                null,
                false);
    }

    private ShipmentDetailRow detailRow(long offerId) {
        return new ShipmentDetailRow(
                offerId,
                new BigDecimal("1416.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("2.45"),
                "SWJJ",
                "Southwest Expedited Transportation LLC",
                "000225",
                "FLAT BED, 30 FT AND LESS",
                "SOPHIA REYESCHUELA",
                "SOPHIA.REYES_CHUELA@US.AF.MIL",
                "{\"bid\":{\"totalAmount\":1416}}",
                null);
    }
}
