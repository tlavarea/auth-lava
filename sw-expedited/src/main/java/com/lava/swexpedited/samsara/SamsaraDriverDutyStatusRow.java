package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * A samsara_driver_duty_status row - one per driver, their current HOS duty status plus the remaining-time HOS clocks
 * Samsara's {@code /fleet/hos/clocks} response carries alongside it. dutyStatusSince isn't part of Samsara's response
 * (its {@code CurrentDutyStatus} has no "since" timestamp) - it's derived by {@code SamsaraDriverDutyStatusSyncTasklet}
 * carrying forward the previous sync's value when dutyStatus is unchanged, or resetting to the sync time when it
 * changed (or the driver is new to this table).
 */
public record SamsaraDriverDutyStatusRow(
        String driverId,
        String dutyStatus,
        Long driveRemainingDurationMs,
        Long shiftRemainingDurationMs,
        Long cycleRemainingDurationMs,
        Long timeUntilBreakDurationMs,
        LocalDateTime dutyStatusSince,
        LocalDateTime syncedAt) {}
