package com.lava.swexpedited.samsara;

/** Where a {@link SamsaraSafetyEvent} occurred. */
public record SamsaraSafetyEventLocation(
        Double latitude,
        Double longitude,
        Integer headingDegrees,
        Double accuracyMeters,
        SamsaraSafetyEventAddress address) {}
