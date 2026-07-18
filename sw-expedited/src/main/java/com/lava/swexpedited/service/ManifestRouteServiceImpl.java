package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves a manifest's driving route (geometry, not just distance/duration) for the Schedule page's manifest-route
 * map, delegating to {@link GoogleRoutesClient#computeRoute} on every request - same live, on-demand, no-persisted-
 * cache convention as {@link SamsaraDriverLiveLocationServiceImpl}, since this is only ever called while a dispatcher
 * has a single manifest's map panel open.
 */
@Service
public class ManifestRouteServiceImpl implements ManifestRouteService {

    private final VektorManifestRepository vektorManifestRepository;
    private final GoogleRoutesClient googleRoutesClient;

    public ManifestRouteServiceImpl(
            VektorManifestRepository vektorManifestRepository, GoogleRoutesClient googleRoutesClient) {
        this.vektorManifestRepository = vektorManifestRepository;
        this.googleRoutesClient = googleRoutesClient;
    }

    @Override
    public Optional<ManifestRouteResponse> findRoute(long manifestNumber) {
        return vektorManifestRepository
                .findByManifestNumber(manifestNumber)
                .filter(manifest -> manifest.destinationLatitude() != null && manifest.destinationLongitude() != null)
                .flatMap(this::computeRoute);
    }

    private Optional<ManifestRouteResponse> computeRoute(VektorManifestRow manifest) {
        return googleRoutesClient
                .computeRoute(
                        manifest.origin(), new LatLng(manifest.destinationLatitude(), manifest.destinationLongitude()))
                .map(route -> toResponse(route, manifest));
    }

    private static ManifestRouteResponse toResponse(ComputedRoute route, VektorManifestRow manifest) {
        return new ManifestRouteResponse(
                route.originLatitude(),
                route.originLongitude(),
                manifest.destinationLatitude(),
                manifest.destinationLongitude(),
                route.encodedPolyline(),
                route.distanceMeters(),
                route.duration());
    }
}
