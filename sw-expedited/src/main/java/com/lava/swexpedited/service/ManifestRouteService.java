package com.lava.swexpedited.service;

import com.lava.swexpedited.manifest.ManifestRouteResponse;
import java.util.Optional;

public interface ManifestRouteService {

    /**
     * Empty when {@code manifestNumber} isn't a known manifest, the manifest's destination hasn't been geocoded yet
     * ({@code destination_latitude}/{@code destination_longitude} null), or Google returns no drivable route between
     * the two points - all expected states, not errors.
     */
    Optional<ManifestRouteResponse> findRoute(long manifestNumber);
}
