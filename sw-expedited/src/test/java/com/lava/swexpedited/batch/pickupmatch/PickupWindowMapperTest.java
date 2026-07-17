package com.lava.swexpedited.batch.pickupmatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PickupWindowMapperTest {

    private final PickupWindowMapper pickupWindowMapper = new PickupWindowMapper();

    @Test
    void map_parsesEarliestAndLatestPickupDateAsEasternLocalDateTime() {
        Instant earliestInstant = Instant.parse("2026-07-20T12:00:00Z");
        Instant latestInstant = Instant.parse("2026-07-21T18:30:00Z");
        String rawResponse = """
                {"bid":{"equipment":{"shipment":{"earliestPickupDate":%d,"latestPickupDate":%d}}}}
                """.formatted(earliestInstant.toEpochMilli(), latestInstant.toEpochMilli());

        PickupWindow window = this.pickupWindowMapper.map(rawResponse);

        assertThat(window).isNotNull();
        assertThat(window.earliest())
                .isEqualTo(LocalDateTime.ofInstant(earliestInstant, ZoneId.of("America/New_York")));
        assertThat(window.latest()).isEqualTo(LocalDateTime.ofInstant(latestInstant, ZoneId.of("America/New_York")));
    }

    @Test
    void map_missingPickupDateFields_returnsNull() {
        PickupWindow window = this.pickupWindowMapper.map("{\"bid\":{\"equipment\":{\"shipment\":{}}}}");

        assertThat(window).isNull();
    }

    @Test
    void map_missingBid_returnsNull() {
        PickupWindow window = this.pickupWindowMapper.map("{}");

        assertThat(window).isNull();
    }

    @Test
    void map_onlyEarliestPickupDatePresent_returnsNull() {
        Instant earliestInstant = Instant.parse("2026-07-20T12:00:00Z");
        String rawResponse = """
                {"bid":{"equipment":{"shipment":{"earliestPickupDate":%d}}}}
                """.formatted(earliestInstant.toEpochMilli());

        PickupWindow window = this.pickupWindowMapper.map(rawResponse);

        assertThat(window).isNull();
    }
}
