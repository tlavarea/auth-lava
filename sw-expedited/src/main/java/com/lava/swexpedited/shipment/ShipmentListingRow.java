package com.lava.swexpedited.shipment;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A shipment listing row, shared by the CSV parser (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch/controller/service layers never need to reference generated code directly.
 *
 * <p>expirationDate/syncedAt are LocalDateTime, not OffsetDateTime, even though the underlying columns are TIMESTAMPTZ
 * - jOOQ's codegen simulates the schema against H2, which doesn't distinguish TIMESTAMPTZ from TIMESTAMP, so the
 * generated POJO fields are LocalDateTime. Matches the same TIMESTAMPTZ-to-LocalDateTime convention already in use in
 * backend (e.g. AuthThrottle.updatedAt).
 *
 * <p>viablePickup is write-only from the CSV parser's perspective - {@code ShipmentCsvParser} always passes
 * {@code false} for it, since {@code ShipmentListingRepository#replaceAll}'s insert column list doesn't include this
 * column at all (it relies on the DB default instead, see 009-add-viable-pickup-to-shipment-listing.yaml); the real
 * value is written afterward by {@code PickupMatchTasklet}, so only the repository's read side (via {@code toRow}) ever
 * returns a meaningful value here.
 */
public record ShipmentListingRow(
        long offerId,
        String status,
        LocalDateTime expirationDate,
        String shipmentId,
        String shipmentType,
        String rank,
        String gbloc,
        String origin,
        String destination,
        String equipType,
        int conveyancesOffered,
        int conveyancesAccepted,
        LocalDate pickupDate,
        LocalDate requiredDeliveryDate,
        LocalDateTime syncedAt,
        boolean viablePickup) {}
