package com.lava.swexpedited.truck;

import java.math.BigDecimal;
import java.time.Instant;
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
 * rather than decomposed into typed fields. {@code locationTime} is an {@link Instant} rather than a
 * {@code LocalDateTime} (unlike {@code syncedAt}) so Jackson serializes it with a {@code Z} suffix the frontend's
 * DatePipe can correctly convert to the viewer's local time zone - {@code SamsaraVehicleLocationRow#locationTime} is
 * Samsara's UTC wall-clock digits with the offset stripped (see {@code SamsaraTasklet#parseLocalDateTime}), so
 * {@code TruckServiceImpl} reattaches {@code ZoneOffset.UTC} when building this response. {@code licensePlate} is
 * joined from samsara_vehicle (the Samsara vehicle roster, not samsara_vehicle_diagnostics/samsara_vehicle_location) by
 * the same {@code matched_samsara_vehicle_id}, null under the same "unmatched" convention.
 */
public record TruckDetailResponse(
        String id,
        String truckNumber,
        Integer statusCode,
        String vin,
        String licensePlate,
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
        Double ecuSpeedMph,
        Double defLevelPercent,
        Double batteryVolts,
        Double coolantTempF,
        Integer engineRpm,
        Integer engineLoadPercent,
        BigDecimal latitude,
        BigDecimal longitude,
        String formattedLocation,
        Instant locationTime) {}
