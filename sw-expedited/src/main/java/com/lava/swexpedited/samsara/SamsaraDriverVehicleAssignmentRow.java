package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/** A samsara_driver_vehicle_assignment row - one per driver, representing their current vehicle assignment. */
public record SamsaraDriverVehicleAssignmentRow(
        String driverId,
        String vehicleId,
        String vehicleName,
        LocalDateTime startTime,
        LocalDateTime assignedAtTime,
        LocalDateTime syncedAt) {}
