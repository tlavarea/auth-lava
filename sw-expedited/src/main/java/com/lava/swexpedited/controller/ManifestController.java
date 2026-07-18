package com.lava.swexpedited.controller;

import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.service.ManifestRouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManifestController {

    private final ManifestRouteService manifestRouteService;

    public ManifestController(ManifestRouteService manifestRouteService) {
        this.manifestRouteService = manifestRouteService;
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
}
