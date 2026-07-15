package com.lava.swexpedited.batch;

import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Logs shipments the detail-fetch step gave up on (see the {@code faultTolerant()}/{@code skip()} configuration on
 * {@code ShipmentSyncJobConfig.shipmentDetailStep}) - that shipment simply has no row in shipment_detail until it's
 * picked up successfully on a later cycle, rather than failing the whole sync.
 */
@Component
@Slf4j
public class ShipmentDetailSkipListener implements SkipListener<ShipmentListingRow, ShipmentDetailRow> {

    @Override
    public void onSkipInProcess(ShipmentListingRow item, Throwable t) {
        log.error("onSkipInProcess::failed to fetch bid detail for offerId: {}", item.offerId(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("onSkipInRead::failed to read a shipment listing row while fetching bid detail", t);
    }

    @Override
    public void onSkipInWrite(ShipmentDetailRow item, Throwable t) {
        log.error("onSkipInWrite::failed to persist bid detail for offerId: {}", item.offerId(), t);
    }
}
