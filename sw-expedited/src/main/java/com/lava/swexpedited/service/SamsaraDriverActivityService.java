package com.lava.swexpedited.service;

import com.lava.swexpedited.samsara.DriverActivityEntry;
import java.time.Instant;
import java.util.List;

public interface SamsaraDriverActivityService {

    /**
     * Newest-first duty-status-change history for {@code driverId} from {@code since} through now. An empty list is a
     * normal result (no status changes in the requested window), not an error - there's no 404 case here the way there
     * is for a driver that isn't currently synced, since this call doesn't depend on samsara_driver at all.
     */
    List<DriverActivityEntry> findActivity(String driverId, Instant since);
}
