package com.lava.swexpedited.service;

import com.lava.swexpedited.shipment.ShipmentDetailResponse;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import java.util.Optional;

public interface ShipmentService {

    List<ShipmentListingRow> findAll();

    /**
     * Empty only when {@code offerId} isn't a currently-listed shipment. A listing with no synced bid detail yet (e.g.
     * its getBid call was skipped after failing this cycle - see ShipmentDetailSkipListener) still returns a response,
     * just with the detail fields null.
     */
    Optional<ShipmentDetailResponse> findDetail(long offerId);
}
