package com.lava.swexpedited.manifest;

import java.math.BigDecimal;

/**
 * The truck's position when the manifest begins, if Vektor reported one - see {@code VektorManifestStartingPosition}.
 */
public record ManifestStartingPositionResponse(
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String note,
        BigDecimal estimatedMilesToNext,
        BigDecimal actualMilesToNext,
        BigDecimal odometerMiles) {}
