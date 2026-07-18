package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;

/**
 * The {@code GET /api/drivers/timeline} response shape: one row per driver, joining their current HOS duty status with
 * whichever vektor_manifest currently matches them (see {@code DriverTimelineService}). {@code dutyStatus} is null
 * under the same conditions as {@code DriverListingRow.dutyStatus}. The manifest fields (everything from
 * {@code manifestStatus} on) are null when no currently-synced manifest matches this driver - i.e. the driver has no
 * known active load - not when a match failed to load; {@code pickupAppointmentStart}/{@code eta} are the load's
 * scheduled pickup/dropoff appointment times (not actual arrival/departure times - see {@code VektorManifestMapper}'s
 * javadoc), used by the timeline view to position and size a driver's "busy" block; {@code origin}/{@code destination}
 * are that block's endpoint labels.
 */
public record DriverTimelineRow(
        String driverId,
        String driverName,
        String activationStatus,
        String dutyStatus,
        String manifestStatus,
        LocalDateTime pickupAppointmentStart,
        LocalDateTime eta,
        String origin,
        String destination,
        String loadReference) {}
