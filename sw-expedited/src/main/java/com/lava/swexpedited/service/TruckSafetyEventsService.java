package com.lava.swexpedited.service;

import com.lava.swexpedited.truck.TruckSafetyEventEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TruckSafetyEventsService {

    /**
     * Empty only when {@code truckId} doesn't resolve to a vektor_truck row - a present but empty list is returned for
     * an existing truck with no matched Samsara vehicle or no safety events in the window. See the impl's javadoc for
     * why this is {@code Optional<List<T>>} rather than a bare {@code List}, unlike
     * {@link SamsaraDriverActivityService#findActivity}.
     */
    Optional<List<TruckSafetyEventEntry>> findSafetyEvents(String truckId, Instant startTime);
}
