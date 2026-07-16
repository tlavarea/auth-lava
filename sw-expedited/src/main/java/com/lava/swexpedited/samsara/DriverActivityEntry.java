package com.lava.swexpedited.samsara;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One entry in the {@code GET /api/drivers/{driverId}/activity} response - a duty-status change from Samsara's
 * {@code /fleet/hos/logs}, fetched live on every request (see {@code SamsaraDriverActivityService}), not persisted.
 * endTime is null for the driver's current (still-open) status. latitude/longitude are null when Samsara recorded no
 * location for that log entry.
 */
public record DriverActivityEntry(
        String dutyStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal latitude,
        BigDecimal longitude,
        String remark) {}
