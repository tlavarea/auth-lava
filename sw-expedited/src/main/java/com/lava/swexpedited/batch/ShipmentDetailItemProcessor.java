package com.lava.swexpedited.batch;

import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Fetches bid detail for one currently-listed shipment. Left to throw rather than catch-and-return-null: the enclosing
 * step (see {@code ShipmentSyncJobConfig.shipmentDetailStep}) is fault-tolerant with a skip policy, so one shipment's
 * failed {@code getBid} call is logged and skipped (via {@code ShipmentDetailSkipListener}) without failing the rest of
 * the cycle - the same "log and move on" outcome the earlier scjj-gfm-app implementation reached with a try/catch, but
 * delegated to Spring Batch's own retry/skip machinery instead of hand-rolled here.
 */
@Component
public class ShipmentDetailItemProcessor implements ItemProcessor<ShipmentListingRow, ShipmentDetailRow> {

    private final GfmBidClient gfmBidClient;

    public ShipmentDetailItemProcessor(GfmBidClient gfmBidClient) {
        this.gfmBidClient = gfmBidClient;
    }

    @Override
    public ShipmentDetailRow process(ShipmentListingRow item) {
        return gfmBidClient.fetchDetail(item.offerId());
    }
}
