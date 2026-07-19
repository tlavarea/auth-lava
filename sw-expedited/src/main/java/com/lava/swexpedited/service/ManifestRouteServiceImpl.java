package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.manifest.ManifestStartingPositionResponse;
import com.lava.swexpedited.manifest.ManifestStopResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorManifestStartingPosition;
import com.lava.swexpedited.vektor.VektorManifestStop;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves a manifest's ordered stops and its driving route through all of them (geometry, not just distance/ duration)
 * for the Schedule page's manifest-route map and detail pane, delegating to {@link GoogleRoutesClient#computeRoute} on
 * every request - same live, on-demand, no-persisted-cache convention as {@link SamsaraDriverLiveLocationServiceImpl},
 * since this is only ever called while a dispatcher has a single manifest's map panel open.
 *
 * <p>The waypoint list also includes the driver's current live location (via {@link ManifestDriverLocationService},
 * Vektor's own - not Samsara's, which needed a fuzzy name-matched join that could simply be missing), spliced in
 * immediately before the first stop that hasn't been checked out of yet - without this, the route is computed purely
 * from Vektor's static stop coordinates and can end up visibly far from where the driver actually is (a real-world
 * route deviation, not just imprecision). The driver's location is resolved once, at the moment this is called, same as
 * everything else here - it isn't re-fetched as the driver keeps moving after a dispatcher opens the map panel.
 */
@Service
public class ManifestRouteServiceImpl implements ManifestRouteService {

    private final VektorManifestRepository vektorManifestRepository;
    private final GoogleRoutesClient googleRoutesClient;
    private final ManifestDriverLocationService manifestDriverLocationService;

    public ManifestRouteServiceImpl(
            VektorManifestRepository vektorManifestRepository,
            GoogleRoutesClient googleRoutesClient,
            ManifestDriverLocationService manifestDriverLocationService) {
        this.vektorManifestRepository = vektorManifestRepository;
        this.googleRoutesClient = googleRoutesClient;
        this.manifestDriverLocationService = manifestDriverLocationService;
    }

    @Override
    public Optional<ManifestRouteResponse> findRoute(long manifestNumber) {
        return vektorManifestRepository
                .findByManifestNumber(manifestNumber)
                .filter(manifest -> !manifest.stops().isEmpty())
                .flatMap(this::computeRoute);
    }

    private Optional<ManifestRouteResponse> computeRoute(VektorManifestRow manifest) {
        List<LatLng> waypoints = waypointsOf(manifest);
        if (waypoints.size() < 2) {
            return Optional.empty();
        }
        return googleRoutesClient.computeRoute(waypoints).map(route -> toResponse(route, manifest));
    }

    private List<LatLng> waypointsOf(VektorManifestRow manifest) {
        LatLng driverLocation = driverLocationOf(manifest).orElse(null);
        boolean driverLocationInserted = false;

        List<LatLng> waypoints = new ArrayList<>();
        VektorManifestStartingPosition startingPosition = manifest.startingPosition();
        if (startingPosition != null && startingPosition.latitude() != null && startingPosition.longitude() != null) {
            waypoints.add(new LatLng(startingPosition.latitude(), startingPosition.longitude()));
        }
        for (VektorManifestStop stop : manifest.stops()) {
            // Splices the driver's location in right before the next stop they haven't finished yet, rather than
            // always appending it last - if every stop is already checked out (shouldn't normally happen while a
            // manifest is still open, but tolerated rather than thrown on), it's left out entirely instead of
            // silently becoming the route's destination waypoint by landing after the real last stop.
            if (!driverLocationInserted && driverLocation != null && stop.checkedOutAt() == null) {
                waypoints.add(driverLocation);
                driverLocationInserted = true;
            }
            if (stop.latitude() != null && stop.longitude() != null) {
                waypoints.add(new LatLng(stop.latitude(), stop.longitude()));
            }
        }
        return waypoints;
    }

    private Optional<LatLng> driverLocationOf(VektorManifestRow manifest) {
        return manifestDriverLocationService
                .findLiveLocation(manifest.manifestNumber())
                .filter(location -> location.latitude() != null && location.longitude() != null)
                .map(location -> new LatLng(location.latitude(), location.longitude()));
    }

    private static ManifestRouteResponse toResponse(ComputedRoute route, VektorManifestRow manifest) {
        return new ManifestRouteResponse(
                manifest.stops().stream()
                        .map(ManifestRouteServiceImpl::toStopResponse)
                        .toList(),
                toStartingPositionResponse(manifest.startingPosition()),
                route.encodedPolyline(),
                route.distanceMeters(),
                route.duration());
    }

    private static ManifestStopResponse toStopResponse(VektorManifestStop stop) {
        return new ManifestStopResponse(
                stop.stopId(),
                stop.sequenceNumber(),
                stop.stopType(),
                stop.siteName(),
                stop.address(),
                stop.latitude(),
                stop.longitude(),
                stop.timezoneAbbreviation(),
                stop.appointmentWindowStart(),
                stop.appointmentWindowEnd(),
                stop.arrivedAt(),
                stop.checkedInAt(),
                stop.checkedOutAt(),
                stop.referenceNumbers(),
                stop.notes(),
                stop.contactPhone(),
                stop.estimatedMilesToNext(),
                stop.actualMilesToNext(),
                stop.odometerMiles());
    }

    private static ManifestStartingPositionResponse toStartingPositionResponse(
            VektorManifestStartingPosition startingPosition) {
        return startingPosition == null
                ? null
                : new ManifestStartingPositionResponse(
                        startingPosition.address(),
                        startingPosition.latitude(),
                        startingPosition.longitude(),
                        startingPosition.note(),
                        startingPosition.estimatedMilesToNext(),
                        startingPosition.actualMilesToNext(),
                        startingPosition.odometerMiles());
    }
}
