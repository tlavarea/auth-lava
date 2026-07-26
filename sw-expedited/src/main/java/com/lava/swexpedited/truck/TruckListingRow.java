package com.lava.swexpedited.truck;

/**
 * One row per synced truck for the Trucks list view. {@code statusCode} is Vektor's raw, unconfirmed status integer
 * (see {@code VektorTruckRow}'s javadoc) - rendered as-is rather than mapped to a semantic label.
 * {@code currentDriverName}/{@code currentTrailerLabel} are resolved from
 * {@code vektor_truck.current_driver_id}/{@code current_trailer_id}, null when unassigned or when the referenced
 * driver/trailer isn't currently synced.
 */
public record TruckListingRow(
        String id, String truckNumber, Integer statusCode, String currentDriverName, String currentTrailerLabel) {}
