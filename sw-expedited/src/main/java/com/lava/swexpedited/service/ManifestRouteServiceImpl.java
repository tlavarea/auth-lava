package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.pickupmatch.GooglePlacesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.RouteWaypoint;
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
 *
 * <p>Google failing to find a drivable route (e.g. a long-haul, backtracking waypoint sequence it declines to route)
 * only blanks the response's route geometry/distance/duration, not the stops or starting position - those are read
 * straight from Vektor and are valid regardless of whether Google could route through them. See {@link #toResponse}.
 *
 * <p>When the exact-coordinate attempt above still comes back empty, this retries with exactly one address-bearing
 * waypoint (a stop, or the starting position - never the driver's live location, which has no address to fall back to)
 * at a time re-expressed as a free-text address instead of its precise coordinate, via {@link GooglePlacesClient} -
 * trying each address-bearing waypoint in turn and stopping at the first one that produces a route, rather than
 * swapping every address-bearing waypoint at once. This matters for gated/restricted-access locations like military
 * installations, which Google's Routes API sometimes excludes from public {@code DRIVE} routing entirely at their exact
 * coordinates, even when Google's own Places API resolves the address just fine - {@link GooglePlacesClient} normalizes
 * Vektor's own address text (which is sometimes abbreviated in a way Google's Routes API geocoder won't expand, e.g.
 * "FT H LIGGETT" rather than "Fort Hunter Liggett") to that place's canonical display name; only the bare display name
 * text is used for the retry (not Places API's own resolved coordinates, which point at the same precise, excluded
 * point the first attempt already failed on) - Routes API's own coarser address geocoding is what actually lands on a
 * routable point. Retrying one waypoint at a time - rather than normalizing every address-bearing waypoint in a single
 * retry - is deliberate: Places API's text search can resolve an already-fine address down to a less specific display
 * name (confirmed in practice - a starting position's full street address resolved to a bare plus code with no
 * city/state, and a dropoff's full address resolved to just its street line), which would trade a working coordinate
 * for a newly-broken one on a waypoint that was never the problem.
 */
@Service
public class ManifestRouteServiceImpl implements ManifestRouteService {

    private final VektorManifestRepository vektorManifestRepository;
    private final GoogleRoutesClient googleRoutesClient;
    private final GooglePlacesClient googlePlacesClient;
    private final ManifestDriverLocationService manifestDriverLocationService;

    public ManifestRouteServiceImpl(
            VektorManifestRepository vektorManifestRepository,
            GoogleRoutesClient googleRoutesClient,
            GooglePlacesClient googlePlacesClient,
            ManifestDriverLocationService manifestDriverLocationService) {
        this.vektorManifestRepository = vektorManifestRepository;
        this.googleRoutesClient = googleRoutesClient;
        this.googlePlacesClient = googlePlacesClient;
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
        List<RoutePoint> points = waypointsOf(manifest);
        if (points.size() < 2) {
            return Optional.empty();
        }
        List<RouteWaypoint> exactWaypoints =
                points.stream().map(RoutePoint::asExactWaypoint).toList();
        ComputedRoute route = googleRoutesClient
                .computeRoute(exactWaypoints)
                .or(() -> retryWithOneAddressNormalized(points, exactWaypoints))
                .orElse(null);
        return Optional.of(toResponse(route, manifest));
    }

    // See this class's javadoc for why this swaps in exactly one address-bearing waypoint's normalized address at a
    // time (keeping every other waypoint at its original coordinate) rather than normalizing all of them in one
    // retry - stops at the first waypoint whose normalized address, substituted in on its own, produces a route.
    private Optional<ComputedRoute> retryWithOneAddressNormalized(
            List<RoutePoint> points, List<RouteWaypoint> exactWaypoints) {
        for (int i = 0; i < points.size(); i++) {
            String address = points.get(i).address();
            if (address == null) {
                continue;
            }
            Optional<RouteWaypoint> normalized =
                    this.googlePlacesClient.resolveDisplayName(address).map(RouteWaypoint::ofAddress);
            if (normalized.isEmpty()) {
                continue;
            }
            List<RouteWaypoint> attempt = new ArrayList<>(exactWaypoints);
            attempt.set(i, normalized.get());
            Optional<ComputedRoute> result = this.googleRoutesClient.computeRoute(attempt);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private List<RoutePoint> waypointsOf(VektorManifestRow manifest) {
        LatLng driverLocation = driverLocationOf(manifest).orElse(null);
        boolean driverLocationInserted = false;

        List<RoutePoint> waypoints = new ArrayList<>();
        VektorManifestStartingPosition startingPosition = manifest.startingPosition();
        if (startingPosition != null && startingPosition.latitude() != null && startingPosition.longitude() != null) {
            waypoints.add(new RoutePoint(
                    new LatLng(startingPosition.latitude(), startingPosition.longitude()), startingPosition.address()));
        }
        for (VektorManifestStop stop : manifest.stops()) {
            // Splices the driver's location in right before the next stop they haven't finished yet, rather than
            // always appending it last - if every stop is already checked out (shouldn't normally happen while a
            // manifest is still open, but tolerated rather than thrown on), it's left out entirely instead of
            // silently becoming the route's destination waypoint by landing after the real last stop.
            if (!driverLocationInserted && driverLocation != null && stop.checkedOutAt() == null) {
                waypoints.add(new RoutePoint(driverLocation, null));
                driverLocationInserted = true;
            }
            if (stop.latitude() != null && stop.longitude() != null) {
                waypoints.add(new RoutePoint(new LatLng(stop.latitude(), stop.longitude()), stop.address()));
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

    // route is null when Google couldn't find a drivable route through this manifest's waypoints (see
    // GoogleRoutesClient#computeRoute's javadoc) - an expected state, not an error, and not a reason to withhold the
    // manifest's stops/starting position too, since those come straight from Vektor and don't depend on Google at all.
    private static ManifestRouteResponse toResponse(ComputedRoute route, VektorManifestRow manifest) {
        return new ManifestRouteResponse(
                manifest.stops().stream()
                        .map(ManifestRouteServiceImpl::toStopResponse)
                        .toList(),
                toStartingPositionResponse(manifest.startingPosition()),
                route == null ? null : route.encodedPolyline(),
                route == null ? null : route.distanceMeters(),
                route == null ? null : route.duration());
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

    // A single waypoint's coordinate plus, if it has one, the address to normalize via GooglePlacesClient on retry
    // (see this class's javadoc) - address is null for the driver's live location, which has no address of its own.
    private record RoutePoint(LatLng latLng, String address) {
        RouteWaypoint asExactWaypoint() {
            return RouteWaypoint.ofLocation(this.latLng);
        }
    }
}
