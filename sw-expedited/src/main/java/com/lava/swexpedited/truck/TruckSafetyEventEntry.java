package com.lava.swexpedited.truck;

import java.time.Instant;
import java.util.List;

/**
 * One entry in the {@code GET /api/trucks/{truckId}/safety-events} response - a Samsara-flagged safety event for the
 * truck's matched vehicle. {@code occurredAt} is derived from Samsara's {@code startMs} (see
 * {@code TruckSafetyEventsService}). {@code address}/{@code mediaUrl} are pre-formatted/pre-selected by
 * {@code TruckSafetyEventsService} from Samsara's raw nested shapes - {@code mediaUrl} is null when the event has no
 * media attached.
 */
public record TruckSafetyEventEntry(
        String id,
        Instant occurredAt,
        List<String> behaviorLabels,
        double latitude,
        double longitude,
        String address,
        String driverName,
        String mediaUrl) {}
