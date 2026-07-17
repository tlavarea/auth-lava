package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for {@code PickupMatchTasklet}'s match constraints: {@code timeWindow} is a coarse,
 * pre-{@code RouteMatrixClient} filter on how close (in time) a driver's {@code vektor_manifest.eta} must fall to a
 * shipment's GFM pickup window - generous by design, since it only decides which shipment/manifest pairs are worth
 * spending a (paid) route matrix lookup on, not whether a pair is actually viable. {@code maxDistanceMiles} bounds
 * road-trip distance between the driver's destination and the shipment's pickup location. {@code arrivalBuffer} is
 * added on top of {@code RouteMatrixClient}'s computed driving duration - not the flat {@code timeWindow} - when
 * deciding whether a driver can actually reach the pickup before the window closes, to leave slack for HOS-mandated
 * breaks the raw driving duration doesn't account for. All three are product decisions (how "close"/"enough slack"
 * counts as viable) rather than technical ones, so they're configurable rather than hardcoded.
 */
@ConfigurationProperties(prefix = "pickup-match")
@Validated
public record PickupMatchProperties(
        @NotNull Duration timeWindow,
        @NotNull Integer maxDistanceMiles,
        @NotNull Duration arrivalBuffer) {}
