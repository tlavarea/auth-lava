package com.lava.swexpedited.vektor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One driver's live-location ping, from Vektor's {@code EntityLocation/GetAll} (field 3, repeated) - keyed directly by
 * Vektor's own {@code driver_id}, unlike Samsara's live location, which needs a fuzzy name-matched join. Multiple
 * entries can exist per driver (a short history), so callers pick whichever is most recent for a given driver.
 * {@code asOf} is Vektor's own epoch-millisecond timestamp, converted to UTC - unlike the {@code yyyy-MM-dd HH:mm:ss}
 * local-wall-clock strings elsewhere in this package (e.g. {@code VektorManifestStop}), this one really is an
 * unambiguous instant on the wire, not a local time needing a separately-reported timezone.
 */
public record VektorDriverLocationRow(
        String driverId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal headingDegrees,
        LocalDateTime asOf,
        String formattedLocation) {}
