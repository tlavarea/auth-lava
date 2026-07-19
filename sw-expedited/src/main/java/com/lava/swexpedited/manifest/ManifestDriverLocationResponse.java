package com.lava.swexpedited.manifest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A manifest's driver's live location, from Vektor's own {@code EntityLocation/GetAll} - see
 * {@code ManifestDriverLocationServiceImpl}'s javadoc for why this replaced the Samsara-sourced equivalent for anything
 * manifest-scoped. No {@code speed} field, unlike Samsara's {@code DriverLiveLocationResponse} - Vektor's location data
 * doesn't report one.
 */
public record ManifestDriverLocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal headingDegrees,
        LocalDateTime asOf,
        String formattedLocation) {}
