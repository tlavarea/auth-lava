package com.lava.swexpedited.vektor;

import java.time.LocalDateTime;

/**
 * A vektor_trailer row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). {@code label} is the combined display string Vektor
 * itself sends (e.g. {@code "T231 - 53' SDL"}) - stored as-is rather than split into a trailer number/type, since not
 * every trailer's label has the {@code " - "} separator observed on most. {@code matchedSamsaraTrailerId} is resolved
 * once per trailer at sync time via {@link VinMatchingTrailerMatchStrategy} against {@code vin} - same best-effort
 * convention as {@code matchedSamsaraVehicleId} on {@link VektorTruckRow}.
 */
public record VektorTrailerRow(
        String id,
        String label,
        String manufacturer,
        Integer year,
        String vin,
        String rawResponse,
        LocalDateTime syncedAt,
        String matchedSamsaraTrailerId) {

    public VektorTrailerRow withMatchedSamsaraTrailerId(String matchedSamsaraTrailerId) {
        return new VektorTrailerRow(id, label, manufacturer, year, vin, rawResponse, syncedAt, matchedSamsaraTrailerId);
    }
}
