package com.lava.swexpedited.truck;

/**
 * One row per synced truck for the Trucks list view. {@code engineState}/{@code ecuSpeedMph} are joined from
 * {@code samsara_vehicle_diagnostics} by {@code vektor_truck.matched_samsara_vehicle_id} the same best-effort way as
 * {@code TruckDetailResponse}'s diagnostic fields - null when the truck isn't VIN-matched to a Samsara vehicle or has
 * no synced diagnostics yet. The frontend derives the displayed status (Off/On/Idle/Moving) from these two fields
 * rather than Vektor's own {@code statusCode}, which is a raw, unconfirmed integer (see {@code VektorTruckRow}'s
 * javadoc) not otherwise surfaced. {@code currentDriverName}/{@code currentTrailerLabel} are resolved from
 * {@code vektor_truck.current_driver_id}/{@code current_trailer_id}, null when unassigned or when the referenced
 * driver/trailer isn't currently synced.
 */
public record TruckListingRow(
        String id,
        String truckNumber,
        String engineState,
        Double ecuSpeedMph,
        String currentDriverName,
        String currentTrailerLabel) {}
