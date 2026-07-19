package com.lava.swexpedited.service;

import com.lava.swexpedited.manifest.ManifestEtaResponse;
import java.util.Optional;

public interface ManifestEtaService {

    /**
     * Empty when the manifest doesn't exist, every stop is already checked out (nothing left to have an ETA for), or
     * Vektor has no ETA snapshot on file targeting the current active stop.
     */
    Optional<ManifestEtaResponse> findEta(long manifestNumber);
}
