package com.lava.swexpedited.vektor;

import java.time.LocalDateTime;

/**
 * A vektor_driver row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). {@code matchedSamsaraDriverId} is resolved once per
 * driver at sync time via {@link NameNormalizingDriverMatchStrategy} against {@code fullName} - deliberately no FK
 * constraint, same best-effort convention {@code vektor_manifest.matched_samsara_driver_id} uses. This replaces the old
 * approach of {@code VektorSyncTasklet} re-running that same match live against every individual manifest/ time-off
 * row.
 */
public record VektorDriverRow(
        String id,
        String driverNumber,
        String fullName,
        String email,
        String phone,
        String matchedSamsaraDriverId,
        String rawResponse,
        LocalDateTime syncedAt) {

    public VektorDriverRow withMatchedSamsaraDriverId(String matchedSamsaraDriverId) {
        return new VektorDriverRow(
                id, driverNumber, fullName, email, phone, matchedSamsaraDriverId, rawResponse, syncedAt);
    }
}
