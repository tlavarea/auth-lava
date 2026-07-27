package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.PaginationResponse;
import java.util.List;

/**
 * The {@code GET /fleet/vehicles/stats/history?types=gps} response envelope - hand-written (unlike every other
 * {@link com.lava.swexpedited.batch.samsara.SamsaraFleetClient} response type) since this endpoint isn't in the
 * vendored, trimmed {@code schema/samsara-api.json} (see that file's own "info.description"). {@code data} has one
 * entry per requested {@code vehicleIds} value - always at most one here, since
 * {@code SamsaraFleetClient#fetchVehicleGpsHistory} only ever queries a single vehicle at a time. Reuses the generated
 * {@link PaginationResponse} and {@link com.lava.swexpedited.samsara.model.VehicleStatsGps} - both are already shaped
 * exactly like this endpoint's pagination envelope and per-point GPS payload (the same one
 * {@code VehicleStatsResponseData.gps} uses), so there's no need for a parallel hand-written GPS point type.
 */
public record SamsaraVehicleGpsHistoryResponse(
        List<SamsaraVehicleGpsHistoryResponseData> data, PaginationResponse pagination) {}
