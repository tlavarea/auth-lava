package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/** A samsara_driver_duty_status row - one per driver, their current HOS duty status. */
public record SamsaraDriverDutyStatusRow(String driverId, String dutyStatus, LocalDateTime syncedAt) {}
