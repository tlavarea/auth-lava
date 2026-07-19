package com.lava.swexpedited.manifest;

import com.lava.swexpedited.vektor.StopType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One stop in a {@link ManifestRouteResponse}'s ordered stop list - the Schedule page's manifest-route map and detail
 * pane render one numbered marker/row per entry, in {@code sequenceNumber} order. Mirrors {@code VektorManifestStop}
 * field-for-field; see that record's javadoc for what each field means and which are expected to be null
 * (arrival/check-in/check-out until reached, mileage-to-next on the last stop).
 */
public record ManifestStopResponse(
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
