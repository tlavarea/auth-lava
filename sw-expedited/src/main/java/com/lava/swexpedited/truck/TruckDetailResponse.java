package com.lava.swexpedited.truck;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detail response for a single truck - {@code currentDriverName}/{@code currentTrailerLabel} resolve the same way as
 * {@link TruckListingRow}'s. The diagnostic and location fields below are only populated when
 * {@code vektor_truck.matched_samsara_vehicle_id} resolved to a Samsara vehicle with synced data - all are null
 * otherwise, same "unassigned or stale id" convention as {@code currentDriverName}/{@code currentTrailerLabel}.
 * Diagnostic values are converted from Samsara's native units (see {@code TruckServiceImpl}) to display units here:
 * odometer in miles, engine hours in hours, DEF level and battery voltage and coolant temperature in their conventional
 * units (percent, volts, Fahrenheit) rather than Samsara's milli-units. {@code faultCodes} is Samsara's raw
 * per-CAN-bus-type fault code JSON, passed through as-is (see {@code VehicleStatsFaultCodes} in samsara-api.json)
 * rather than decomposed into typed fields.
 */
public record TruckDetailResponse(
        String id,
        String truckNumber,
        Integer statusCode,
        String vin,
        String make,
        String model,
        Integer year,
        String currentDriverName,
        String currentTrailerLabel,
        LocalDateTime syncedAt,
        Integer fuelPercent,
        Double odometerMiles,
        Double engineHours,
        String faultCodes,
        String engineState,
        Double defLevelPercent,
        Double batteryVolts,
        Double coolantTempF,
        Integer engineRpm,
        Integer engineLoadPercent,
        BigDecimal latitude,
        BigDecimal longitude,
        String formattedLocation,
        LocalDateTime locationTime) {}
