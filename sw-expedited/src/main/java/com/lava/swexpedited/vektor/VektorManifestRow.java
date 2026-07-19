package com.lava.swexpedited.vektor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A vektor_manifest row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch layer never needs to reference generated code directly - same convention as
 * {@code SamsaraDriverRow}/{@code ShipmentListingRow}.
 *
 * <p>{@code origin}/{@code destination}/{@code destinationLatitude}/{@code destinationLongitude}/
 * {@code pickupAppointmentStart}/{@code eta} stay derived from just the first pickup and last dropoff stop (see
 * {@link VektorManifestMapper}) for the Schedule grid's compact per-driver segment bars. {@code stops} is the full,
 * ordered stop-by-stop detail (every pickup/dropoff on the manifest, not just the first/last) backing the Schedule
 * page's manifest-route map and detail pane; {@code startingPosition} is the truck's position when the manifest begins,
 * if Vektor reported one.
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
        List<VektorManifestStop> stops,
        VektorManifestStartingPosition startingPosition,
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
                stops,
                startingPosition,
                rawResponse,
                syncedAt);
    }
}
