package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import java.util.List;

/**
 * One vehicle's GPS history within a {@link SamsaraVehicleGpsHistoryResponse} - {@code gps} is time-ordered per
 * Samsara's docs.
 */
public record SamsaraVehicleGpsHistoryResponseData(String id, String name, List<VehicleStatsGps> gps) {}
