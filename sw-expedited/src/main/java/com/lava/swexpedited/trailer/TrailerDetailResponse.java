package com.lava.swexpedited.trailer;

import java.time.LocalDateTime;

/**
 * {@code currentTruckNumber}/{@code currentDriverName} are reverse lookups over {@code vektor_truck.current_trailer_id}
 * and, from that truck, {@code current_driver_id} - a trailer has no assignment column of its own, same convention as
 * {@link TrailerListingRow}. {@code licensePlate}/ {@code assetSerialNumber} come from the Samsara trailer matched via
 * {@code matched_samsara_trailer_id} (see {@code VinMatchingTrailerMatchStrategy}) - null when unmatched.
 */
public record TrailerDetailResponse(
        String id,
        String label,
        String manufacturer,
        Integer year,
        String vin,
        String licensePlate,
        String assetSerialNumber,
        String currentTruckNumber,
        String currentDriverName,
        LocalDateTime syncedAt) {}
