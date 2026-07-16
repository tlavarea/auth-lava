package com.lava.swexpedited.samsara;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A samsara_vehicle_location row - one per vehicle, the most recent GPS fix synced from Samsara. */
public record SamsaraVehicleLocationRow(
        String vehicleId,
        String vehicleName,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal heading,
        BigDecimal speed,
        LocalDateTime locationTime,
        String formattedLocation,
        LocalDateTime syncedAt) {}
