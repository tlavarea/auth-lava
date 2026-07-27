package com.lava.swexpedited.truck;

import java.util.List;

/**
 * The {@code GET /api/trucks/{truckId}/route-history} response - the truck detail page's route map draws {@code points}
 * as a polyline and {@code stops} as bullseye markers. Both are empty (never null) when the truck exists but isn't
 * matched to a Samsara vehicle, or has no GPS history for the requested window - see {@code TruckRouteHistoryService}'s
 * javadoc for the 404-vs-empty-200 policy this response participates in.
 */
public record TruckRouteHistoryResponse(List<TruckRoutePoint> points, List<TruckRouteStop> stops) {}
