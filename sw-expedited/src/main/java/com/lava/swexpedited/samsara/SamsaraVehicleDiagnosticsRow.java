package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * A samsara_vehicle_diagnostics row - one per vehicle, the most recent engine/fuel/fault snapshot synced from Samsara.
 * All value columns are individually nullable - a vehicle can be offline for one stat type and reporting for another
 * within the same sync (see {@code SamsaraFleetClient#fetchVehicleDiagnostics}). Values are kept in Samsara's native
 * units (milli-percent, milli-volts, milli-Celsius, meters, seconds) rather than converted at write time - conversion
 * to display units happens in {@code TruckServiceImpl}, so this row stays a faithful, lossless copy of what Samsara
 * reported.
 */
public record SamsaraVehicleDiagnosticsRow(
        String vehicleId,
        Integer fuelPercent,
        Long odometerMeters,
        Long engineSeconds,
        String faultCodes,
        String engineState,
        Double ecuSpeedMph,
        Integer defLevelMilliPercent,
        Integer batteryMilliVolts,
        Integer coolantTempMilliC,
        Integer engineRpm,
        Integer engineLoadPercent,
        LocalDateTime syncedAt) {}
