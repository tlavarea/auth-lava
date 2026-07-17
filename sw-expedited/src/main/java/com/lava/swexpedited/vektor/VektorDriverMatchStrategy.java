package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.util.List;
import java.util.Optional;

/**
 * Best-effort join from a Vektor manifest's driver to our existing Samsara driver roster - there's no shared identifier
 * between the two systems (confirmed during investigation: Vektor's driver UUIDs are entirely its own), so this is
 * pluggable rather than hardcoded to one join field. Returns empty rather than guessing when a match is ambiguous or
 * unavailable; {@code vektor_manifest.matched_samsara_driver_id} has no FK constraint precisely because this is
 * best-effort, not referential integrity.
 */
public interface VektorDriverMatchStrategy {

    Optional<String> match(VektorManifestRow row, List<SamsaraDriverRow> candidates);
}
