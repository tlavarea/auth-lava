package com.lava.swexpedited.samsara;

import java.util.List;

/**
 * One entry in a {@link SamsaraSafetyEventsResponse} - a single Samsara-flagged safety event for one vehicle/driver.
 * {@code startMs} (epoch millis) is what {@code TruckSafetyEventsService} uses as "when did this happen" - not
 * {@code createdAtTime}/{@code updatedAtTime} (Samsara's own record-bookkeeping timestamps, which can lag the real
 * event) or {@code tripStartTime}/{@code tripEndTime} (the whole trip's window, too coarse). {@code behaviorLabels} and
 * {@code media} are left in their raw nested shapes here rather than flattened to a {@code List<String>}/single URL -
 * {@code TruckSafetyEventsService} does that flattening when mapping to {@code TruckSafetyEventEntry}, keeping this
 * record a plain mirror of the wire JSON like every other hand-written Samsara type in this package.
 */
public record SamsaraSafetyEvent(
        String id,
        Long startMs,
        List<SamsaraSafetyEventBehaviorLabel> behaviorLabels,
        SamsaraSafetyEventLocation location,
        SamsaraSafetyEventDriver driver,
        List<SamsaraSafetyEventMedia> media,
        String incidentReportUrl) {}
