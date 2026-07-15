package com.lava.swexpedited.repository;

import com.lava.swexpedited.shipment.ShipmentDetailRow;
import java.util.List;
import java.util.Optional;

public interface ShipmentDetailRepository {

    /**
     * Inserts {@code rows}, one per currently-listed shipment offer whose bid detail was fetched successfully this
     * cycle. Plain inserts rather than an upsert: shipment_detail is FK'd to shipment_listing with {@code ON DELETE
     * CASCADE}, so the listing replace that runs before this in the same job (see
     * {@code ShipmentListingRepository.replaceAll}) already empties this table for offers no longer present.
     */
    void insertAll(List<ShipmentDetailRow> rows);

    Optional<ShipmentDetailRow> findByOfferId(long offerId);
}
