package com.lava.swexpedited.vektor;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Default {@link VektorDriverMatchStrategy}: normalizes both names to lowercase, letters-only, alphabetically-sorted
 * words (so case, punctuation, extra whitespace, and first/last-name order all wash out) and matches on equality.
 * Returns empty on zero or more-than-one match rather than guessing - an ambiguous match (e.g. two drivers who happen
 * to normalize the same) is worse than no match, since {@code matched_samsara_driver_id} feeds a future "shipments near
 * this driver" feature that depends on it being correct, not just present.
 */
@Component
public class NameNormalizingDriverMatchStrategy implements VektorDriverMatchStrategy {

    @Override
    public Optional<String> match(String driverName, List<SamsaraDriverRow> candidates) {
        if (driverName == null) {
            return Optional.empty();
        }

        String normalizedTarget = normalize(driverName);
        List<SamsaraDriverRow> matches = candidates.stream()
                .filter(candidate ->
                        candidate.name() != null && normalize(candidate.name()).equals(normalizedTarget))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst().id()) : Optional.empty();
    }

    private static String normalize(String name) {
        String lettersAndSpacesOnly =
                name.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", "").trim();
        if (lettersAndSpacesOnly.isEmpty()) {
            return "";
        }
        return Arrays.stream(lettersAndSpacesOnly.split("\\s+")).sorted().collect(Collectors.joining(" "));
    }
}
