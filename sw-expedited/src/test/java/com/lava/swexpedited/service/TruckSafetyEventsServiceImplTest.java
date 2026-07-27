package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraSafetyEvent;
import com.lava.swexpedited.samsara.SamsaraSafetyEventAddress;
import com.lava.swexpedited.samsara.SamsaraSafetyEventBehaviorLabel;
import com.lava.swexpedited.samsara.SamsaraSafetyEventDriver;
import com.lava.swexpedited.samsara.SamsaraSafetyEventLocation;
import com.lava.swexpedited.samsara.SamsaraSafetyEventMedia;
import com.lava.swexpedited.truck.TruckSafetyEventEntry;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TruckSafetyEventsServiceImplTest {

    private static final Instant START_TIME = Instant.parse("2026-07-27T00:00:00Z");

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private SamsaraFleetClient samsaraFleetClient;

    @Test
    void findSafetyEvents_unknownTruckId_isEmpty() {
        when(this.vektorTruckRepository.findById("bad-id")).thenReturn(Optional.empty());
        TruckSafetyEventsServiceImpl service =
                new TruckSafetyEventsServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        assertThat(service.findSafetyEvents("bad-id", START_TIME)).isEmpty();
    }

    @Test
    void findSafetyEvents_truckWithNoMatchedVehicle_returnsEmptyList() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow(null)));
        TruckSafetyEventsServiceImpl service =
                new TruckSafetyEventsServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        Optional<List<TruckSafetyEventEntry>> result = service.findSafetyEvents("truck-1", START_TIME);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void findSafetyEvents_flattensBehaviorLabelsAddressDriverAndFirstMediaUrl() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        SamsaraSafetyEvent event = new SamsaraSafetyEvent(
                "evt-1",
                1785312000000L,
                List.of(new SamsaraSafetyEventBehaviorLabel("Harsh Brake", "Camera")),
                new SamsaraSafetyEventLocation(
                        32.735,
                        -97.108,
                        180,
                        5.0,
                        new SamsaraSafetyEventAddress("100 Main St", "Fort Worth", "TX", "76102")),
                new SamsaraSafetyEventDriver("41000123", "Jane Trucker"),
                List.of(
                        new SamsaraSafetyEventMedia("dashcamRoadFacing", null),
                        new SamsaraSafetyEventMedia("dashcamDriverFacing", "https://example.com/clip.mp4")),
                "https://cloud.samsara.com/report/evt-1");
        when(this.samsaraFleetClient.fetchSafetyEvents("281474", START_TIME)).thenReturn(List.of(event));
        TruckSafetyEventsServiceImpl service =
                new TruckSafetyEventsServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        List<TruckSafetyEventEntry> entries =
                service.findSafetyEvents("truck-1", START_TIME).orElseThrow();

        assertThat(entries).hasSize(1);
        TruckSafetyEventEntry entry = entries.getFirst();
        assertThat(entry.id()).isEqualTo("evt-1");
        assertThat(entry.occurredAt()).isEqualTo(Instant.ofEpochMilli(1785312000000L));
        assertThat(entry.behaviorLabels()).containsExactly("Harsh Brake");
        assertThat(entry.latitude()).isEqualTo(32.735);
        assertThat(entry.longitude()).isEqualTo(-97.108);
        assertThat(entry.address()).isEqualTo("100 Main St, Fort Worth, TX, 76102");
        assertThat(entry.driverName()).isEqualTo("Jane Trucker");
        assertThat(entry.mediaUrl()).isEqualTo("https://example.com/clip.mp4");
    }

    @Test
    void findSafetyEvents_noMedia_mediaUrlIsNull() {
        when(this.vektorTruckRepository.findById("truck-1")).thenReturn(Optional.of(truckRow("281474")));
        SamsaraSafetyEvent event = new SamsaraSafetyEvent(
                "evt-1",
                1785312000000L,
                List.of(),
                new SamsaraSafetyEventLocation(32.735, -97.108, null, null, null),
                null,
                List.of(),
                null);
        when(this.samsaraFleetClient.fetchSafetyEvents("281474", START_TIME)).thenReturn(List.of(event));
        TruckSafetyEventsServiceImpl service =
                new TruckSafetyEventsServiceImpl(this.vektorTruckRepository, this.samsaraFleetClient);

        TruckSafetyEventEntry entry =
                service.findSafetyEvents("truck-1", START_TIME).orElseThrow().getFirst();

        assertThat(entry.mediaUrl()).isNull();
        assertThat(entry.address()).isNull();
        assertThat(entry.driverName()).isNull();
    }

    private static VektorTruckRow truckRow(String matchedSamsaraVehicleId) {
        return new VektorTruckRow(
                "truck-1",
                "1234",
                1,
                "1XPBD49X7ND764317",
                "Peterbilt",
                "579",
                2022,
                null,
                null,
                "{}",
                LocalDateTime.now(),
                matchedSamsaraVehicleId);
    }
}
