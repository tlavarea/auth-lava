package com.lava.swexpedited.vektor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One stop (pickup or dropoff) on a manifest's route, in the order Vektor returns them - the full detail
 * {@link VektorManifestMapper}'s origin/destination fields collapse away. Field numbers this is built from are
 * documented on {@link VektorManifestMapper}.
 *
 * <p>{@code arrivedAt}/{@code checkedInAt}/{@code checkedOutAt} are null until the driver actually reaches/checks
 * in/checks out of this stop - a manifest mid-route has these populated for its completed stops and null for the rest,
 * which is also how a stop's "Completed"/"Arrived"/"En Route" status is inferred (Vektor has no separate status field
 * per stop; the frontend derives it from these three timestamps).
 *
 * <p>{@code estimatedMilesToNext}/{@code actualMilesToNext}/{@code odometerMiles} describe this stop's outbound leg to
 * the following stop (both are {@code null}/zero on the last stop, which has no next leg) - Vektor reports these
 * per-stop rather than per-leg, so they're read off the stop that precedes the leg they describe.
 *
 * <p>{@code stopId} is Vektor's own per-stop identifier (distinct from {@code sequenceNumber}, which is just this
 * stop's position within the manifest) - it's what {@code Manifests/TruckEtaStatesGet} snapshots use to say which stop
 * an ETA calculation targets, so it's needed to correlate a live ETA back to a specific stop.
 */
public record VektorManifestStop(
        String stopId,
        int sequenceNumber,
        StopType stopType,
        String siteName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String timezoneAbbreviation,
        LocalDateTime appointmentWindowStart,
        LocalDateTime appointmentWindowEnd,
        LocalDateTime arrivedAt,
        LocalDateTime checkedInAt,
        LocalDateTime checkedOutAt,
        String referenceNumbers,
        String notes,
        String contactPhone,
        BigDecimal estimatedMilesToNext,
        BigDecimal actualMilesToNext,
        BigDecimal odometerMiles) {}
