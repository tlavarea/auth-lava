package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipmentDetailItemProcessorTest {

    @Mock
    private GfmBidClient gfmBidClient;

    @Test
    void process_delegatesToGfmBidClientUsingItemOfferId() {
        ShipmentListingRow item = new ShipmentListingRow(
                1284311010L,
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
                null);
        ShipmentDetailRow expected =
                new ShipmentDetailRow(1284311010L, null, null, null, null, null, null, null, null, null, "{}", null);
        when(gfmBidClient.fetchDetail(1284311010L)).thenReturn(expected);
        ShipmentDetailItemProcessor processor = new ShipmentDetailItemProcessor(gfmBidClient);

        ShipmentDetailRow result = processor.process(item);

        assertThat(result).isEqualTo(expected);
    }
}
