package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default {@link VektorTrailerMatchStrategy}: normalizes both identifiers (uppercase, trimmed) and matches on exact
 * equality against {@link SamsaraTrailerRow#vin()} - same reasoning as {@link VinMatchingTruckMatchStrategy}, no fuzzy
 * matching, since a VIN is a structured identifier rather than free text. Matches against {@code vin} (read from the
 * Samsara trailer's {@code externalIds} map, see {@code SamsaraTrailerSyncTasklet}), not
 * {@link SamsaraTrailerRow#trailerSerialNumber()} - a live capture showed {@code trailerSerialNumber} populated for
 * only half of a trailer roster that had {@code externalIds.samsara.vin} on every trailer. Returns empty on zero or
 * more-than-one match rather than guessing, same reasoning as {@link VinMatchingTruckMatchStrategy} - an ambiguous
 * match is worse than no match.
 */
@Component
public class VinMatchingTrailerMatchStrategy implements VektorTrailerMatchStrategy {

    @Override
    public Optional<String> match(String vin, List<SamsaraTrailerRow> candidates) {
        if (vin == null) {
            return Optional.empty();
        }

        String normalizedTarget = normalize(vin);
        if (normalizedTarget.isEmpty()) {
            return Optional.empty();
        }

        List<SamsaraTrailerRow> matches = candidates.stream()
                .filter(candidate ->
                        candidate.vin() != null && normalize(candidate.vin()).equals(normalizedTarget))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst().id()) : Optional.empty();
    }

    private static String normalize(String vin) {
        return vin.toUpperCase(Locale.ROOT).trim();
    }
}
