package com.lava.swexpedited.vektor;

import java.time.LocalDateTime;

/**
 * A vektor_truck row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). {@code currentTrailerId}/{@code currentDriverId} are
 * Vektor's own UUIDs for its current trailer/driver assignment (matching {@code vektor_trailer.id}/
 * {@code vektor_driver.id} respectively) - deliberately no FK constraint, same best-effort convention
 * {@code matched_samsara_driver_id} columns elsewhere use, since this is derived from Vektor's own undocumented data
 * rather than referential integrity this app controls. {@code currentDriverId} is what
 * {@code VektorTruckRepository#findCurrentDriverIdByTruckId} exposes for {@code VektorSyncTasklet}'s time-off
 * attribution, replacing the old {@code VektorManifestRepository#findLatestDriverIdByTruckId} heuristic.
 * {@code matchedSamsaraVehicleId} is resolved once per truck at sync time via {@link VinMatchingTruckMatchStrategy}
 * against {@code vin} - same best-effort convention as {@code matchedSamsaraDriverId} on {@link VektorDriverRow}.
 */
public record VektorTruckRow(
        String id,
        String truckNumber,
        Integer statusCode,
        String vin,
        String make,
        String model,
        Integer year,
        String currentTrailerId,
        String currentDriverId,
        String rawResponse,
        LocalDateTime syncedAt,
        String matchedSamsaraVehicleId) {

    public VektorTruckRow withMatchedSamsaraVehicleId(String matchedSamsaraVehicleId) {
        return new VektorTruckRow(
                id,
                truckNumber,
                statusCode,
                vin,
                make,
                model,
                year,
                currentTrailerId,
                currentDriverId,
                rawResponse,
                syncedAt,
                matchedSamsaraVehicleId);
    }
}
