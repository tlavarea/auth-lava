package com.lava.swexpedited.repository;

import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import java.util.Optional;

public interface ShipmentListingRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction. The CSV this is sourced from is a
     * live snapshot of what's currently available, not a stream of changes to merge, so a sync run has nothing to diff
     * against the previous one - it just becomes the new truth.
     */
    void replaceAll(List<ShipmentListingRow> rows);

    List<ShipmentListingRow> findAll();

    Optional<ShipmentListingRow> findByOfferId(long offerId);
}
