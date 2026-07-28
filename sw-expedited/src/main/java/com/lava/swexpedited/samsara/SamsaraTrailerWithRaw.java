package com.lava.swexpedited.samsara;

import com.lava.swexpedited.samsara.model.Trailer;

/**
 * A trailer from {@code GET /fleet/trailers} paired with the exact raw JSON of its list entry, for persisting into
 * samsara_trailer.raw_response - see {@code SamsaraFleetClient.fetchTrailers()}.
 */
public record SamsaraTrailerWithRaw(Trailer payload, String rawJson) {}
