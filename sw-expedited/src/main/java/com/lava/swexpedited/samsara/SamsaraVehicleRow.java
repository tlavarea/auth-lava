package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * A samsara_vehicle row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch/service layers never need to reference generated code directly - same convention as
 * {@code SamsaraDriverRow}.
 */
public record SamsaraVehicleRow(
        String id,
        String vin,
        String name,
        String make,
        String model,
        String year,
        String licensePlate,
        String rawResponse,
        LocalDateTime syncedAt) {}
