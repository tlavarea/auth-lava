package com.lava.swexpedited.truck;

import java.time.LocalDateTime;

/**
 * Placeholder detail response for a single truck - {@code currentDriverName}/{@code currentTrailerLabel} resolve the
 * same way as {@link TruckListingRow}'s.
 */
public record TruckDetailResponse(
        String id,
        String truckNumber,
        Integer statusCode,
        String vin,
        String make,
        String model,
        Integer year,
        String currentDriverName,
        String currentTrailerLabel,
        LocalDateTime syncedAt) {}
