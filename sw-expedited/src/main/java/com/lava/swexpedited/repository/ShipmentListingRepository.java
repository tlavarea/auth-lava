package com.lava.swexpedited.repository;

import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ShipmentListingRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction. The CSV this is sourced from is a
     * live snapshot of what's currently available, not a stream of changes to merge, so a sync run has nothing to diff
     * against the previous one - it just becomes the new truth.
     */
    void replaceAll(List<ShipmentListingRow> rows);

    List<ShipmentListingRow> findAll();

    Optional<ShipmentListingRow> findByOfferId(long offerId);

    /**
     * Sets {@code viable_pickup = true} for exactly {@code offerIds}, leaving every other row at whatever value it
     * already has. Called by {@code PickupMatchTasklet} after {@code replaceAll} has already run this same job - every
     * row starts {@code false} from that fresh insert (see 009-add-viable-pickup-to-shipment-listing.yaml's column
     * default), so there's no need for this to reset non-matching rows itself.
     */
    void markViablePickups(Set<Long> offerIds);
}
