package com.lava.swexpedited.vektor;

import java.math.BigDecimal;

/**
 * The truck's position when a manifest begins - a synthetic, non-numbered "stop" (Vektor stop field 1 == 2) carried
 * over from wherever the driver's previous manifest left off, rather than a real pickup/dropoff on this manifest. Null
 * on a manifest with no such entry.
 *
 * <p>{@code estimatedMilesToNext}/{@code actualMilesToNext}/{@code odometerMiles} describe the leg from here to the
 * first real stop, same as the trailing three fields on {@link VektorManifestStop}.
 */
public record VektorManifestStartingPosition(
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String note,
        BigDecimal estimatedMilesToNext,
        BigDecimal actualMilesToNext,
        BigDecimal odometerMiles) {}
