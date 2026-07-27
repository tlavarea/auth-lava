package com.lava.swexpedited.truck;

import java.time.Instant;

/** One raw GPS sample making up a {@link TruckRouteHistoryResponse}'s polyline, time-ordered. */
public record TruckRoutePoint(
        Instant time, double latitude, double longitude, Integer headingDegrees, Double speedMph) {}
