package com.lava.swexpedited.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ShipmentListingRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private ShipmentListingRepository shipmentListingRepository;

    @Test
    void findAll_noRows_isEmpty() {
        assertThat(this.shipmentListingRepository.findAll()).isEmpty();
    }

    @Test
    void replaceAll_populatesTable() {
        this.shipmentListingRepository.replaceAll(
                List.of(row(1284311010L, "KLFV160850003"), row(1284314723L, "BKMT160890012")));

        List<ShipmentListingRow> found = this.shipmentListingRepository.findAll();

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ShipmentListingRow::offerId).containsExactlyInAnyOrder(1284311010L, 1284314723L);
        assertThat(found).allSatisfy(shipment -> assertThat(shipment.syncedAt()).isNotNull());
    }

    @Test
    void replaceAll_calledAgain_replacesPreviousRows() {
        this.shipmentListingRepository.replaceAll(List.of(row(1284311010L, "KLFV160850003")));

        this.shipmentListingRepository.replaceAll(List.of(row(1284314723L, "BKMT160890012")));

        List<ShipmentListingRow> found = this.shipmentListingRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().offerId()).isEqualTo(1284314723L);
    }

    @Test
    void replaceAll_emptyList_clearsTable() {
        this.shipmentListingRepository.replaceAll(List.of(row(1284311010L, "KLFV160850003")));

        this.shipmentListingRepository.replaceAll(List.of());

        assertThat(this.shipmentListingRepository.findAll()).isEmpty();
    }

    @Test
    void findByOfferId_noRow_isEmpty() {
        assertThat(this.shipmentListingRepository.findByOfferId(1284311010L)).isEmpty();
    }

    @Test
    void findByOfferId_matchingRow_returnsIt() {
        this.shipmentListingRepository.replaceAll(
                List.of(row(1284311010L, "KLFV160850003"), row(1284314723L, "BKMT160890012")));

        assertThat(this.shipmentListingRepository.findByOfferId(1284311010L))
                .isPresent()
                .get()
                .extracting(ShipmentListingRow::shipmentId)
                .isEqualTo("KLFV160850003");
    }

    @Test
    void markViablePickups_setsFlagOnlyForGivenOfferIds() {
        this.shipmentListingRepository.replaceAll(
                List.of(row(1284311010L, "KLFV160850003"), row(1284314723L, "BKMT160890012")));

        this.shipmentListingRepository.markViablePickups(Set.of(1284311010L));

        assertThat(this.shipmentListingRepository.findByOfferId(1284311010L))
                .get()
                .extracting(ShipmentListingRow::viablePickup)
                .isEqualTo(true);
        assertThat(this.shipmentListingRepository.findByOfferId(1284314723L))
                .get()
                .extracting(ShipmentListingRow::viablePickup)
                .isEqualTo(false);
    }

    @Test
    void markViablePickups_emptySet_doesNothing() {
        this.shipmentListingRepository.replaceAll(List.of(row(1284311010L, "KLFV160850003")));

        this.shipmentListingRepository.markViablePickups(Set.of());

        assertThat(this.shipmentListingRepository.findByOfferId(1284311010L))
                .get()
                .extracting(ShipmentListingRow::viablePickup)
                .isEqualTo(false);
    }

    @Test
    void replaceAll_resetsViablePickupOnFreshInsert() {
        this.shipmentListingRepository.replaceAll(List.of(row(1284311010L, "KLFV160850003")));
        this.shipmentListingRepository.markViablePickups(Set.of(1284311010L));

        this.shipmentListingRepository.replaceAll(List.of(row(1284311010L, "KLFV160850003")));

        assertThat(this.shipmentListingRepository.findByOfferId(1284311010L))
                .get()
                .extracting(ShipmentListingRow::viablePickup)
                .isEqualTo(false);
    }

    private ShipmentListingRow row(long offerId, String shipmentId) {
        return new ShipmentListingRow(
                offerId,
                "Open",
                LocalDateTime.now().plusDays(1),
                shipmentId,
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
}
