package com.lava.swexpedited.service;

import com.lava.swexpedited.samsara.DriverTimelineRow;
import java.time.LocalDateTime;
import java.util.List;

public interface DriverTimelineService {

    /** One row per driver, with every manifest whose scheduled pickup->dropoff window overlaps [weekStart, weekEnd). */
    List<DriverTimelineRow> findForWeek(LocalDateTime weekStart, LocalDateTime weekEnd);
}
