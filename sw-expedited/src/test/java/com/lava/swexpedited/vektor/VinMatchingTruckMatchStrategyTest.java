package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VinMatchingTruckMatchStrategyTest {

    private final VinMatchingTruckMatchStrategy strategy = new VinMatchingTruckMatchStrategy();

    @Test
    void match_exactVin_matches() {
        Optional<String> matched =
                strategy.match("1XPBD49X7ND764317", List.of(vehicle("samsara-1", "1XPBD49X7ND764317")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_differentCaseAndWhitespace_stillMatches() {
        Optional<String> matched =
                strategy.match(" 1xpbd49x7nd764317 ", List.of(vehicle("samsara-1", "1XPBD49X7ND764317")));

        assertThat(matched).contains("samsara-1");
    }

    @Test
    void match_noCandidateMatches_isEmpty() {
        Optional<String> matched =
                strategy.match("1XPBD49X7ND764317", List.of(vehicle("samsara-1", "1XK1D49X0NJ123612")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_ambiguousMultipleCandidates_isEmpty() {
        Optional<String> matched = strategy.match(
                "1XPBD49X7ND764317",
                List.of(vehicle("samsara-1", "1XPBD49X7ND764317"), vehicle("samsara-2", "1xpbd49x7nd764317")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_noVin_isEmpty() {
        Optional<String> matched = strategy.match(null, List.of(vehicle("samsara-1", "1XPBD49X7ND764317")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_blankVin_isEmpty() {
        Optional<String> matched = strategy.match("   ", List.of(vehicle("samsara-1", "1XPBD49X7ND764317")));

        assertThat(matched).isEmpty();
    }

    @Test
    void match_candidateWithNullVin_isSkippedRatherThanThrowing() {
        Optional<String> matched = strategy.match(
                "1XPBD49X7ND764317", List.of(vehicle("samsara-1", null), vehicle("samsara-2", "1XPBD49X7ND764317")));

        assertThat(matched).contains("samsara-2");
    }

    private SamsaraVehicleRow vehicle(String id, String vin) {
        return new SamsaraVehicleRow(id, vin, null, null, null, null, null, "{}", null);
    }
}
