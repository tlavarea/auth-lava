package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default {@link VektorTruckMatchStrategy}: normalizes both VINs (uppercase, trimmed) and matches on exact equality -
 * unlike {@link NameNormalizingDriverMatchStrategy}, no fuzzy matching is applied, since a VIN is a structured
 * identifier rather than free text. Returns empty on zero or more-than-one match rather than guessing, same reasoning
 * as {@link NameNormalizingDriverMatchStrategy} - an ambiguous match is worse than no match.
 */
@Component
public class VinMatchingTruckMatchStrategy implements VektorTruckMatchStrategy {

    @Override
    public Optional<String> match(String vin, List<SamsaraVehicleRow> candidates) {
        if (vin == null) {
            return Optional.empty();
        }

        String normalizedTarget = normalize(vin);
        if (normalizedTarget.isEmpty()) {
            return Optional.empty();
        }

        List<SamsaraVehicleRow> matches = candidates.stream()
                .filter(candidate ->
                        candidate.vin() != null && normalize(candidate.vin()).equals(normalizedTarget))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst().id()) : Optional.empty();
    }

    private static String normalize(String vin) {
        return vin.toUpperCase(Locale.ROOT).trim();
    }
}
