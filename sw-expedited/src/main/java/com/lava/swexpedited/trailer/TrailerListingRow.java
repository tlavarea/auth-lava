package com.lava.swexpedited.trailer;

/**
 * One row per synced trailer for the Trailers list view. {@code currentTruckNumber} is a reverse lookup over
 * {@code vektor_truck.current_trailer_id} - null when no truck currently claims this trailer.
 */
public record TrailerListingRow(
        String id, String label, String manufacturer, Integer year, String currentTruckNumber) {}
