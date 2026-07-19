package com.lava.swexpedited.controller;

import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import com.lava.swexpedited.manifest.ManifestEtaResponse;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.service.ManifestDriverLocationService;
import com.lava.swexpedited.service.ManifestEtaService;
import com.lava.swexpedited.service.ManifestRouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManifestController {

    private final ManifestRouteService manifestRouteService;
    private final ManifestDriverLocationService manifestDriverLocationService;
    private final ManifestEtaService manifestEtaService;

    public ManifestController(
            ManifestRouteService manifestRouteService,
            ManifestDriverLocationService manifestDriverLocationService,
            ManifestEtaService manifestEtaService) {
        this.manifestRouteService = manifestRouteService;
        this.manifestDriverLocationService = manifestDriverLocationService;
        this.manifestEtaService = manifestEtaService;
    }

    /**
     * The driving route between {@code manifestNumber}'s origin and destination, for the Schedule page's manifest-route
     * map - see {@link ManifestRouteService}'s javadoc for the conditions under which this 404s.
     */
    @GetMapping("/api/manifests/{manifestNumber}/route")
    public ResponseEntity<ManifestRouteResponse> route(@PathVariable long manifestNumber) {
        return manifestRouteService
                .findRoute(manifestNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code manifestNumber}'s driver's live location, sourced from Vektor rather than Samsara - see
     * {@link ManifestDriverLocationService}'s javadoc for why. Polled by the Schedule page's manifest-route map for its
     * live driver marker.
     */
    @GetMapping("/api/manifests/{manifestNumber}/driver-location")
    public ResponseEntity<ManifestDriverLocationResponse> driverLocation(@PathVariable long manifestNumber) {
        return manifestDriverLocationService
                .findLiveLocation(manifestNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code manifestNumber}'s live ETA to its current active stop - see {@link ManifestEtaService}'s javadoc for the
     * conditions under which this 404s.
     */
    @GetMapping("/api/manifests/{manifestNumber}/eta")
    public ResponseEntity<ManifestEtaResponse> eta(@PathVariable long manifestNumber) {
        return manifestEtaService
                .findEta(manifestNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
