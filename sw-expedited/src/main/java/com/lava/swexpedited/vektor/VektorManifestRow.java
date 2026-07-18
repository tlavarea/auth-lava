package com.lava.swexpedited.vektor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A vektor_manifest row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch layer never needs to reference generated code directly - same convention as
 * {@code SamsaraDriverRow}/{@code ShipmentListingRow}.
 */
public record VektorManifestRow(
        Long manifestNumber,
        String manifestId,
        String driverId,
        String driverName,
        String matchedSamsaraDriverId,
        String status,
        String origin,
        String destination,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        LocalDateTime pickupAppointmentStart,
        LocalDateTime eta,
        String loadReference,
        String rawResponse,
        LocalDateTime syncedAt) {

    public VektorManifestRow withMatchedSamsaraDriverId(String matchedSamsaraDriverId) {
        return new VektorManifestRow(
                manifestNumber,
                manifestId,
                driverId,
                driverName,
                matchedSamsaraDriverId,
                status,
                origin,
                destination,
                destinationLatitude,
                destinationLongitude,
                pickupAppointmentStart,
                eta,
                loadReference,
                rawResponse,
                syncedAt);
    }
}
