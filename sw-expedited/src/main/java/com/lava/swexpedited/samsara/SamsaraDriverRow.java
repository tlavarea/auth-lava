package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * A samsara_driver row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch/controller/service layers never need to reference generated code directly - same convention as
 * {@code ShipmentListingRow}.
 */
public record SamsaraDriverRow(
        String id,
        String name,
        String username,
        String email,
        String phone,
        String licenseNumber,
        String licenseState,
        String activationStatus,
        String tags,
        LocalDateTime createdAtTime,
        LocalDateTime updatedAtTime,
        String rawResponse,
        LocalDateTime syncedAt) {}
