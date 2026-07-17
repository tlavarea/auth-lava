package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NameNormalizingDriverMatchStrategyTest {

    private final NameNormalizingDriverMatchStrategy strategy = new NameNormalizingDriverMatchStrategy();

    @Test
    void match_exactName_matches() {
        Optional<String> matched =
                strategy.match(row("Warren Ruawhare"), List.of(driver("samsara-1", "Warren Ruawhare")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_differentCaseAndPunctuation_stillMatches() {
        Optional<String> matched =
                strategy.match(row("warren  ruawhare."), List.of(driver("samsara-1", "Warren Ruawhare")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_reversedWordOrder_stillMatches() {
        Optional<String> matched =
                strategy.match(row("Ruawhare Warren"), List.of(driver("samsara-1", "Warren Ruawhare")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_noCandidateMatches_isEmpty() {
        Optional<String> matched = strategy.match(row("Warren Ruawhare"), List.of(driver("samsara-1", "Kelly Dunn")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_ambiguousMultipleCandidates_isEmpty() {
        Optional<String> matched = strategy.match(
                row("Warren Ruawhare"),
                List.of(driver("samsara-1", "Warren Ruawhare"), driver("samsara-2", "warren ruawhare")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_noDriverNameOnManifest_isEmpty() {
        Optional<String> matched = strategy.match(row(null), List.of(driver("samsara-1", "Warren Ruawhare")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_candidateWithNullName_isSkippedRatherThanThrowing() {
        Optional<String> matched = strategy.match(
                row("Warren Ruawhare"), List.of(driver("samsara-1", null), driver("samsara-2", "Warren Ruawhare")));

        assertThat(matched).contains("samsara-2");
    }

    private VektorManifestRow row(String driverName) {
        return new VektorManifestRow(
                1000589L,
                "manifest-uuid",
                "driver-uuid",
                driverName,
                null,
                "manifest_in_progress",
                null,
                null,
                null,
                null,
                null,
                null,
                "{}",
                null);
    }

    private SamsaraDriverRow driver(String id, String name) {
        return new SamsaraDriverRow(id, name, null, null, null, null, null, "active", null, null, null, "{}", null);
    }
}
