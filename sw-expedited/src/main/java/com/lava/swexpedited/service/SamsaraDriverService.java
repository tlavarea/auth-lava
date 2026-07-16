package com.lava.swexpedited.service;

import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraDriverService {

    List<DriverListingRow> findAll();

    /**
     * Empty only when {@code driverId} isn't a currently-active, synced driver. A driver with no current vehicle
     * assignment, or an assigned vehicle with no synced location, still returns a response - just with the
     * assignment/location fields null (see {@link DriverDetailResponse}'s javadoc).
     */
    Optional<DriverDetailResponse> findDetail(String driverId);
}
