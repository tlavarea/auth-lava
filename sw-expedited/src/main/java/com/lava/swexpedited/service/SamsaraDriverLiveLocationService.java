package com.lava.swexpedited.service;

import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import java.util.Optional;

public interface SamsaraDriverLiveLocationService {

    /**
     * Empty when {@code driverId} isn't a currently-synced driver with a current vehicle assignment, or the live
     * Samsara call returns no GPS payload for that vehicle - both expected states, not errors.
     */
    Optional<DriverLiveLocationResponse> findLiveLocation(String driverId);
}
