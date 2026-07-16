package com.lava.swexpedited.samsara;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The {@code GET /api/drivers/{driverId}} response body: every typed samsara_driver column plus whichever
 * assignment/location fields are currently known. Unlike {@code ShipmentDetailResponse}, this doesn't nest a separate
 * "listing" object - samsara_driver is already the one rich table backing both the list and detail views, there's no
 * smaller upstream row to nest.
 *
 * <p>currentVehicleId/currentVehicleName are null when the driver has no current assignment synced; the location fields
 * (latitude through formattedLocation) are null when there's no assignment, or the assigned vehicle has no location
 * synced yet - both are expected states, not errors, and the frontend renders them as "not available" rather than
 * treating a null as a failed request. dutyStatus is null when no HOS clock data has been synced for the driver yet, or
 * their Samsara Driver app is disconnected.
 */
public record DriverDetailResponse(
        String id,
        String name,
        String username,
        String email,
        String phone,
        String licenseNumber,
        String licenseState,
        String activationStatus,
        String dutyStatus,
        String tags,
        String currentVehicleId,
        String currentVehicleName,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal heading,
        BigDecimal speed,
        LocalDateTime locationTime,
        String formattedLocation,
        @JsonRawValue String rawResponse,
        LocalDateTime syncedAt) {}
