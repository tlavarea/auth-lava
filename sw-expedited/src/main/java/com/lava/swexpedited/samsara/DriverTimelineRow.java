package com.lava.swexpedited.samsara;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The {@code GET /api/drivers/timeline} response shape: one row per driver, joining their current HOS duty status with
 * every vektor_manifest whose scheduled pickup->dropoff window overlaps the requested week (see
 * {@code DriverTimelineService#findForWeek}). {@code dutyStatus} is null under the same conditions as
 * {@code DriverListingRow.dutyStatus}, and - unlike {@code manifests} - is always the driver's <em>current</em> status
 * regardless of which week is being viewed, since duty status has no history of its own. {@code manifests} is empty
 * when no manifest matching this driver overlaps the requested week - i.e. the driver has no load that week - not when
 * a match failed to load. {@code timeOff} is likewise every vektor_time_off block whose [startAt, endAt] window
 * overlaps the requested week (see {@code VektorTimeOffRepository#findByWindow}), empty when this driver has none.
 */
public record DriverTimelineRow(
        String driverId,
        String driverName,
        String activationStatus,
        String dutyStatus,
        List<ManifestSegment> manifests,
        List<TimeOffSegment> timeOff) {

    /**
     * One manifest's schedule-relevant fields. {@code pickupAppointmentStart}/{@code eta} are the load's scheduled
     * pickup/dropoff appointment times (not actual arrival/departure times - see {@code VektorManifestMapper}'s
     * javadoc), used by the timeline view to position and size a segment on a driver's row; {@code origin}/
     * {@code destination} are that segment's endpoint labels. {@code manifestNumber} is the stable ID a client uses to
     * look up this specific manifest's route via {@code GET /api/manifests/{manifestNumber}/route}.
     */
    public record ManifestSegment(
            Long manifestNumber,
            String manifestStatus,
            LocalDateTime pickupAppointmentStart,
            LocalDateTime eta,
            String origin,
            String destination,
            String loadReference) {}

    /**
     * One time-off block's schedule-relevant fields, positioned/sized on a driver's row the same way a
     * {@link ManifestSegment} is, using {@code startAt}/{@code endAt} instead of pickup/eta.
     */
    public record TimeOffSegment(String id, LocalDateTime startAt, LocalDateTime endAt, String reason) {}
}
