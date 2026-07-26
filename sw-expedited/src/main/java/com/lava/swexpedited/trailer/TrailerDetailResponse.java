package com.lava.swexpedited.trailer;

import java.time.LocalDateTime;

/** Placeholder detail response for a single trailer. */
public record TrailerDetailResponse(
        String id, String label, String manufacturer, Integer year, LocalDateTime syncedAt) {}
