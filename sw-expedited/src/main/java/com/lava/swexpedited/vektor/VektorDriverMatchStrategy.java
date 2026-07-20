package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort join from a Vektor driver's name to our existing Samsara driver roster - there's no shared identifier
 * between the two systems (confirmed during investigation: Vektor's driver UUIDs are entirely its own), so this is
 * pluggable rather than hardcoded to one join field. Takes a plain name rather than a {@code VektorManifestRow} so it's
 * reusable anywhere a Vektor driver name needs matching, not just manifests - e.g. {@code vektor_time_off}, whose
 * entries only resolve to a driver name indirectly (via truck_id, see {@code VektorSyncTasklet}). Returns empty rather
 * than guessing when a match is ambiguous or unavailable; {@code matched_samsara_driver_id} columns have no FK
 * constraint precisely because this is best-effort, not referential integrity.
 */
public interface VektorDriverMatchStrategy {

    Optional<String> match(String driverName, List<SamsaraDriverRow> candidates);
}
