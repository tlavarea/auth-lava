package com.lava.swexpedited.service;

import com.lava.swexpedited.truck.TruckRouteHistoryResponse;
import java.time.Instant;
import java.util.Optional;

public interface TruckRouteHistoryService {

    /** Empty only when {@code truckId} doesn't resolve to a vektor_truck row - see the impl's javadoc. */
    Optional<TruckRouteHistoryResponse> findRouteHistory(String truckId, Instant startTime, Instant endTime);
}
