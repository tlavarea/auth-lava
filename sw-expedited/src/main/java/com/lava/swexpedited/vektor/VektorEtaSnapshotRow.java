package com.lava.swexpedited.vektor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One ETA calculation snapshot from Vektor's {@code Manifests/TruckEtaStatesGet}, targeting a single stop on a manifest
 * - real captured traffic for a multi-day manifest returned thousands of these (roughly one per minute of driving), so
 * callers should pick the most recent snapshot for whichever stop they care about, not assume there's only one.
 * {@code estimatedArrival} is Vektor's own precomputed ETA, not something this app calculates - cross- validated
 * exactly against a real dispatch sheet (see {@code ManifestEtaServiceImpl}'s javadoc).
 * {@code truckLatitude}/{@code truckLongitude} are the truck's live position at the moment this snapshot was
 * calculated, not the target stop's own (fixed) coordinates - those are already available via
 * {@code VektorManifestStop.latitude()}/{@code longitude()} for the same stop and aren't duplicated here.
 */
public record VektorEtaSnapshotRow(
        String targetStopId,
        int targetSequenceNumber,
        BigDecimal truckLatitude,
        BigDecimal truckLongitude,
        BigDecimal remainingMiles,
        int remainingMinutes,
        LocalDateTime estimatedArrival) {}
