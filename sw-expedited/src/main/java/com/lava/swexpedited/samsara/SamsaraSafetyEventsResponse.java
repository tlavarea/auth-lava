package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.PaginationResponse;
import java.util.List;

/**
 * The {@code GET /safety-events/stream} response envelope - hand-written for the same reason as
 * {@link SamsaraVehicleGpsHistoryResponse} (this endpoint isn't in the vendored {@code schema/samsara-api.json}
 * either). Reuses the generated {@link PaginationResponse}.
 */
public record SamsaraSafetyEventsResponse(List<SamsaraSafetyEvent> data, PaginationResponse pagination) {}
