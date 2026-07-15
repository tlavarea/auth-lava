package com.lava.swexpedited.service;

import com.lava.swexpedited.shipment.OfferResponseRequest;
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

    /**
     * Submits a carrier's accept/decline response for the given offer to GFM/ATR.
     *
     * <p><b>Not implemented yet.</b> This app has only ever had read access to GFM/ATR (the {@code getBid} sync) -
     * GFM's real submit-response contract hasn't been researched or confirmed, and given this would take a real,
     * hard-to-reverse action against a live DoD production system, always throws until that contract is confirmed and
     * this is deliberately wired up for real.
     */
    void respondToOffer(long offerId, OfferResponseRequest request);
}
