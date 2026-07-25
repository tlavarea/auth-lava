package com.lava.swexpedited.vektor;

import java.time.LocalDateTime;

/**
 * A vektor_trailer row, shared by the sync tasklet (write side - {@code syncedAt} is null, set by the repository at
 * insert time) and the repository's read side (fully populated). {@code label} is the combined display string Vektor
 * itself sends (e.g. {@code "T231 - 53' SDL"}) - stored as-is rather than split into a trailer number/type, since not
 * every trailer's label has the {@code " - "} separator observed on most.
 */
public record VektorTrailerRow(
        String id, String label, String manufacturer, Integer year, String rawResponse, LocalDateTime syncedAt) {}
