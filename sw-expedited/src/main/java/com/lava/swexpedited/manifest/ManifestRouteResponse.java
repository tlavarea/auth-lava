package com.lava.swexpedited.manifest;

import java.math.BigDecimal;

/**
 * The {@code GET /api/manifests/{manifestNumber}/route} response shape: a driving route between a manifest's origin and
 * destination, for the Schedule page's manifest-route map. {@code originLatitude}/{@code originLongitude} come from
 * Google's route response (vektor_manifest only stores the destination's coordinates - the origin is address-only);
 * {@code destinationLatitude}/{@code destinationLongitude} are the manifest's own already-geocoded destination.
 * {@code encodedPolyline} is Google's polyline-encoded route geometry, meant to be decoded client-side via
 * {@code google.maps.geometry.encoding.decodePath}.
 */
public record ManifestRouteResponse(
        BigDecimal originLatitude,
        BigDecimal originLongitude,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        String encodedPolyline,
        Long distanceMeters,
        String duration) {}
