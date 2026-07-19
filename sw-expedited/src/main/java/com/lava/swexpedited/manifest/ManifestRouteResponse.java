package com.lava.swexpedited.manifest;

import java.util.List;

/**
 * The {@code GET /api/manifests/{manifestNumber}/route} response shape: a manifest's ordered stops plus the driving
 * route (geometry, not just distance/duration) that visits all of them in order, for the Schedule page's manifest-route
 * map and detail pane. {@code stops} is every real stop on the manifest (see {@code VektorManifestStop} - not just the
 * first/last {@code VektorManifestRow.origin()}/{@code destination()} collapse to); {@code startingPosition} is the
 * truck's position when the manifest begins, if Vektor reported one, and (when present) is the route's actual starting
 * waypoint rather than {@code stops.getFirst()}. {@code encodedPolyline} is Google's polyline-encoded route geometry
 * through every waypoint in order, meant to be decoded client-side via
 * {@code google.maps.geometry.encoding.decodePath}.
 */
public record ManifestRouteResponse(
        List<ManifestStopResponse> stops,
        ManifestStartingPositionResponse startingPosition,
        String encodedPolyline,
        Long distanceMeters,
        String duration) {}
