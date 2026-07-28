package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * A samsara_trailer row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). Kept independent of the jOOQ-generated persistence
 * types so the batch/service layers never need to reference generated code directly - same convention as
 * {@code SamsaraVehicleRow}. {@code vin} is read from the trailer's {@code externalIds} map under the
 * {@code "samsara.vin"} key - Samsara's real Trailer schema has no top-level vin field - and is matched against
 * {@code vektor_trailer.vin} by {@code VinMatchingTrailerMatchStrategy}. {@code trailerSerialNumber} is a separate,
 * physical-serial-number field that's often blank and is not used for matching, only display.
 */
public record SamsaraTrailerRow(
        String id,
        String vin,
        String name,
        String licensePlate,
        String trailerSerialNumber,
        String rawResponse,
        LocalDateTime syncedAt) {}
