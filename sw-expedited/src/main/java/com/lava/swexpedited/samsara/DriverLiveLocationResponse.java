package com.lava.swexpedited.samsara;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The {@code GET /api/drivers/{driverId}/location} response body - a live, on-demand single-vehicle GPS fetch (see
 * {@code SamsaraDriverLiveLocationService}), not read from samsara_vehicle_location. Lets the driver detail screen poll
 * one open driver's position faster than {@code SamsaraLocationSyncScheduler}'s roster-wide ~1 min cadence, without
 * re-fetching the whole fleet.
 */
public record DriverLiveLocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal heading,
        BigDecimal speed,
        LocalDateTime locationTime,
        String formattedLocation) {}
