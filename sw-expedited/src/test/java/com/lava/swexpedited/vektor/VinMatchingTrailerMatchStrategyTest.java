package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VinMatchingTrailerMatchStrategyTest {

    private final VinMatchingTrailerMatchStrategy strategy = new VinMatchingTrailerMatchStrategy();

    @Test
    void match_exactVin_matches() {
        Optional<String> matched =
                strategy.match("5MC125315H5165489", List.of(trailer("samsara-1", "5MC125315H5165489")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_differentCaseAndWhitespace_stillMatches() {
        Optional<String> matched =
                strategy.match(" 5mc125315h5165489 ", List.of(trailer("samsara-1", "5MC125315H5165489")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_noCandidateMatches_isEmpty() {
        Optional<String> matched =
                strategy.match("5MC125315H5165489", List.of(trailer("samsara-1", "1M9EU5327PT001234")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_ambiguousMultipleCandidates_isEmpty() {
        Optional<String> matched = strategy.match(
                "5MC125315H5165489",
                List.of(trailer("samsara-1", "5MC125315H5165489"), trailer("samsara-2", "5mc125315h5165489")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_noVin_isEmpty() {
        Optional<String> matched = strategy.match(null, List.of(trailer("samsara-1", "5MC125315H5165489")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_blankVin_isEmpty() {
        Optional<String> matched = strategy.match("   ", List.of(trailer("samsara-1", "5MC125315H5165489")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_candidateWithNullVin_isSkippedRatherThanThrowing() {
        Optional<String> matched = strategy.match(
                "5MC125315H5165489", List.of(trailer("samsara-1", null), trailer("samsara-2", "5MC125315H5165489")));

        assertThat(matched).contains("samsara-2");
    }

    @Test
    void match_ignoresTrailerSerialNumberEvenWhenItMatches() {
        SamsaraTrailerRow candidate =
                new SamsaraTrailerRow("samsara-1", "1M9EU5327PT001234", null, null, "5MC125315H5165489", "{}", null);

        Optional<String> matched = strategy.match("5MC125315H5165489", List.of(candidate));

        assertThat(matched).isEmpty();
    }

    private SamsaraTrailerRow trailer(String id, String vin) {
        return new SamsaraTrailerRow(id, vin, null, null, null, "{}", null);
    }
}
