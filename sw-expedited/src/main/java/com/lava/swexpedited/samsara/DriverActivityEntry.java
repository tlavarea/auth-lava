package com.lava.swexpedited.samsara;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry in the {@code GET /api/drivers/{driverId}/activity} response - a duty-status change from Samsara's
 * {@code /fleet/hos/logs}, fetched live on every request (see {@code SamsaraDriverActivityService}), not persisted.
 * endTime is null for the driver's current (still-open) status. latitude/longitude are null when Samsara recorded no
 * location for that log entry. startTime/endTime are true instants (not a wall-clock LocalDateTime) so they survive
 * JSON round-tripping without silently absorbing the server's local timezone - see SamsaraDriverActivityServiceImpl.
 */
public record DriverActivityEntry(
        String dutyStatus,
        Instant startTime,
        Instant endTime,
        BigDecimal latitude,
        BigDecimal longitude,
        String remark) {}
