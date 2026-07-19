package com.lava.swexpedited.service;

import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import java.util.Optional;

public interface ManifestDriverLocationService {

    /**
     * Empty when the manifest doesn't exist, has no driver id at all (shouldn't normally happen for a real manifest),
     * or Vektor has no location on file for that driver.
     */
    Optional<ManifestDriverLocationResponse> findLiveLocation(long manifestNumber);
}
