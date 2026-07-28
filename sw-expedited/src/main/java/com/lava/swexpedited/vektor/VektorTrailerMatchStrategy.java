package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort join from a Vektor trailer's VIN to our synced Samsara trailer roster - there's no shared identifier
 * between the two systems, same reasoning as {@link VektorTruckMatchStrategy}. Matches against
 * {@link SamsaraTrailerRow#vin()}, which is read from the Samsara trailer's {@code externalIds} map (Samsara's real
 * Trailer schema has no top-level vin field) rather than {@link SamsaraTrailerRow#trailerSerialNumber()} - a separate,
 * often-blank field kept only for display. Returns empty rather than guessing when a match is ambiguous or unavailable;
 * {@code matched_samsara_trailer_id} has no FK constraint precisely because this is best-effort, not referential
 * integrity.
 */
public interface VektorTrailerMatchStrategy {

    Optional<String> match(String vin, List<SamsaraTrailerRow> candidates);
}
