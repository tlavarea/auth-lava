package com.lava.swexpedited.manifest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A manifest's live ETA to its current active stop (the first stop not yet checked out of) - see
 * {@code ManifestEtaServiceImpl}'s javadoc. {@code estimatedArrival} is Vektor's own precomputed value, not something
 * this app calculates.
 */
public record ManifestEtaResponse(
        int stopSequenceNumber, BigDecimal remainingMiles, int remainingMinutes, LocalDateTime estimatedArrival) {}
