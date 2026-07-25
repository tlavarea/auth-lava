package com.lava.swexpedited.service;

import com.lava.swexpedited.manifest.ManifestRouteResponse;
import java.util.Optional;

public interface ManifestRouteService {

    /**
     * Empty when {@code manifestNumber} isn't a known manifest, it has no stops, or fewer than two of its waypoints
     * (starting position, driver location, stops) are geocoded - all expected states, not errors. When present,
     * {@code stops}/{@code startingPosition} are always populated from Vektor; the route geometry fields
     * ({@code encodedPolyline}/{@code distanceMeters}/{@code duration}) are null instead if Google couldn't find a
     * drivable route through them, rather than that also emptying the whole result.
     */
    Optional<ManifestRouteResponse> findRoute(long manifestNumber);
}
