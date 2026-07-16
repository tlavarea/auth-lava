package com.lava.swexpedited.samsara;

/**
 * The {@code GET /api/drivers} response shape: only the fields the driver list/card-grid UI needs.
 * {@code currentVehicleName} is null when the driver has no current vehicle assignment synced. {@code dutyStatus} is
 * null when no HOS clock data has been synced for the driver yet, or their Samsara Driver app is disconnected.
 * {@code currentLocation} is null under the same conditions as {@code currentVehicleName}, or when the assigned vehicle
 * has no synced location yet.
 */
public record DriverListingRow(
        String id,
        String name,
        String activationStatus,
        String currentVehicleName,
        String dutyStatus,
        String currentLocation) {}
