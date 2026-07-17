package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.GfmBidDetailMapper;
import com.lava.swexpedited.shipment.OfferResponseRequest;
import com.lava.swexpedited.shipment.OfferResponseType;
import com.lava.swexpedited.shipment.ShipmentDetailResponse;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceImplTest {

    @Mock
    private ShipmentListingRepository shipmentListingRepository;

    @Mock
    private ShipmentDetailRepository shipmentDetailRepository;

    @Test
    void findAll_delegatesToRepository() {
        List<ShipmentListingRow> rows = List.of(listingRow(1L));
        when(this.shipmentListingRepository.findAll()).thenReturn(rows);
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(
                this.shipmentListingRepository, this.shipmentDetailRepository, new GfmBidDetailMapper());

        assertThat(shipmentService.findAll()).isEqualTo(rows);
    }

    @Test
    void findDetail_noMatchingListing_isEmpty() {
        when(this.shipmentListingRepository.findByOfferId(1L)).thenReturn(Optional.empty());
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(
                this.shipmentListingRepository, this.shipmentDetailRepository, new GfmBidDetailMapper());

        assertThat(shipmentService.findDetail(1L)).isEmpty();
    }

    @Test
    void findDetail_listingWithoutSyncedDetail_returnsListingWithNullDetailFields() {
        when(this.shipmentListingRepository.findByOfferId(1L)).thenReturn(Optional.of(listingRow(1L)));
        when(this.shipmentDetailRepository.findByOfferId(1L)).thenReturn(Optional.empty());
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(
                this.shipmentListingRepository, this.shipmentDetailRepository, new GfmBidDetailMapper());

        Optional<ShipmentDetailResponse> result = shipmentService.findDetail(1L);

        assertThat(result).isPresent();
        assertThat(result.get().listing()).isEqualTo(listingRow(1L));
        assertThat(result.get().rawResponse()).isNull();
        assertThat(result.get().scac()).isNull();
        assertThat(result.get().bidDetail()).isNull();
    }

    @Test
    void findDetail_listingWithSyncedDetail_returnsCombinedResponse() {
        when(this.shipmentListingRepository.findByOfferId(1L)).thenReturn(Optional.of(listingRow(1L)));
        ShipmentDetailRow detail =
                new ShipmentDetailRow(1L, null, null, null, "SWJJ", null, null, null, null, null, "{\"bid\":{}}", null);
        when(this.shipmentDetailRepository.findByOfferId(1L)).thenReturn(Optional.of(detail));
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(
                this.shipmentListingRepository, this.shipmentDetailRepository, new GfmBidDetailMapper());

        Optional<ShipmentDetailResponse> result = shipmentService.findDetail(1L);

        assertThat(result).isPresent();
        assertThat(result.get().scac()).isEqualTo("SWJJ");
        assertThat(result.get().rawResponse()).isEqualTo("{\"bid\":{}}");
        assertThat(result.get().bidDetail()).isNotNull();
        assertThat(result.get().bidDetail().rins()).isNull();
    }

    @Test
    void respondToOffer_alwaysThrowsNotImplemented() {
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(
                this.shipmentListingRepository, this.shipmentDetailRepository, new GfmBidDetailMapper());

        assertThatThrownBy(
                        () -> shipmentService.respondToOffer(1L, new OfferResponseRequest(OfferResponseType.ACCEPT, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("501")
                .hasMessageContaining("wired up");
    }

    private ShipmentListingRow listingRow(long offerId) {
        return new ShipmentListingRow(
                offerId,
                "Open",
                null,
                "SHIP1",
                "FAK",
                "1",
                "GBLOC",
                "origin",
                "destination",
                "AF2",
                1,
                0,
                null,
                null,
                null,
                false);
    }
}
