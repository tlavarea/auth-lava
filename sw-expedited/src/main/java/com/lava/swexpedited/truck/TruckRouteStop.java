package com.lava.swexpedited.truck;

import java.time.Instant;

/**
 * One place the truck stopped for at least 5 minutes during a {@link TruckRouteHistoryResponse}'s window - see
 * {@code TruckRouteHistoryService}'s javadoc for how contiguous stopped GPS samples are clustered into these.
 * {@code latitude}/{@code longitude}/{@code formattedLocation} are the cluster's centroid/most representative
 * reverse-geocoded address, not necessarily any single sample's exact values.
 */
public record TruckRouteStop(
        double latitude,
        double longitude,
        String formattedLocation,
        Instant arrivalTime,
        Instant departureTime,
        long stoppedMinutes) {}
