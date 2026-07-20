package com.lava.swexpedited.vektor;

import java.time.LocalDateTime;

/**
 * A vektor_time_off row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). {@code truckId} is Vektor's own truck identifier -
 * {@code TruckTimeOff/Get} groups entries by truck, not driver (see {@code VektorManifestMapper}'s javadoc) -
 * {@code matchedSamsaraDriverId} is resolved at sync time by looking up the most-recently-active driver for that truck
 * (see {@code VektorManifestRepository#findLatestDriverIdByTruckId}), the same best-effort, no-FK-constraint convention
 * {@code vektor_manifest.matched_samsara_driver_id} uses.
 */
public record VektorTimeOffRow(
        String id,
        String truckId,
        String matchedSamsaraDriverId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String reason,
        String rawResponse,
        LocalDateTime syncedAt) {

    public VektorTimeOffRow withMatchedSamsaraDriverId(String matchedSamsaraDriverId) {
        return new VektorTimeOffRow(id, truckId, matchedSamsaraDriverId, startAt, endAt, reason, rawResponse, syncedAt);
    }
}
