package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraSafetyEvent;
import com.lava.swexpedited.samsara.SamsaraSafetyEventAddress;
import com.lava.swexpedited.samsara.SamsaraSafetyEventBehaviorLabel;
import com.lava.swexpedited.samsara.SamsaraSafetyEventLocation;
import com.lava.swexpedited.samsara.SamsaraSafetyEventMedia;
import com.lava.swexpedited.truck.TruckSafetyEventEntry;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves {@code truckId}'s {@code matchedSamsaraVehicleId} from the already-synced {@link VektorTruckRepository} and
 * calls {@link SamsaraFleetClient#fetchSafetyEvents} live, on every request - same on-demand, not-persisted pattern as
 * {@link TruckRouteHistoryServiceImpl}. Flattens {@link SamsaraSafetyEvent}'s raw nested
 * {@code behaviorLabels}/{@code location}/{@code media} shapes into {@link TruckSafetyEventEntry}'s flatter fields.
 *
 * <p>Returns {@code Optional<List<TruckSafetyEventEntry>>} rather than a bare {@code List} (unlike
 * {@link SamsaraDriverActivityService#findActivity}, otherwise the closest analog) specifically so "truck not found"
 * and "found, zero events" stay distinguishable at the controller - see this interface's javadoc for the 404 policy
 * that depends on that distinction.
 */
@Service
@Transactional(readOnly = true)
public class TruckSafetyEventsServiceImpl implements TruckSafetyEventsService {

    private final VektorTruckRepository vektorTruckRepository;
    private final SamsaraFleetClient samsaraFleetClient;

    public TruckSafetyEventsServiceImpl(
            VektorTruckRepository vektorTruckRepository, SamsaraFleetClient samsaraFleetClient) {
        this.vektorTruckRepository = vektorTruckRepository;
        this.samsaraFleetClient = samsaraFleetClient;
    }

    @Override
    public Optional<List<TruckSafetyEventEntry>> findSafetyEvents(String truckId, Instant startTime) {
        return vektorTruckRepository.findById(truckId).map(truck -> toEntries(truck, startTime));
    }

    private List<TruckSafetyEventEntry> toEntries(VektorTruckRow truck, Instant startTime) {
        String vehicleId = truck.matchedSamsaraVehicleId();
        if (vehicleId == null) {
            return List.of();
        }
        return samsaraFleetClient.fetchSafetyEvents(vehicleId, startTime).stream()
                .map(TruckSafetyEventsServiceImpl::toEntry)
                .toList();
    }

    private static TruckSafetyEventEntry toEntry(SamsaraSafetyEvent event) {
        SamsaraSafetyEventLocation location = event.location();
        return new TruckSafetyEventEntry(
                event.id(),
                Instant.ofEpochMilli(event.startMs()),
                event.behaviorLabels().stream()
                        .map(SamsaraSafetyEventBehaviorLabel::label)
                        .toList(),
                location != null && location.latitude() != null ? location.latitude() : 0.0,
                location != null && location.longitude() != null ? location.longitude() : 0.0,
                location != null ? formatAddress(location.address()) : null,
                event.driver() != null ? event.driver().name() : null,
                firstMediaUrl(event.media()));
    }

    private static @Nullable String formatAddress(@Nullable SamsaraSafetyEventAddress address) {
        if (address == null) {
            return null;
        }
        String formatted = Stream.of(address.street(), address.city(), address.state(), address.postalCode())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        return StringUtils.isNotBlank(formatted) ? formatted : null;
    }

    private static @Nullable String firstMediaUrl(@Nullable List<SamsaraSafetyEventMedia> media) {
        if (media == null) {
            return null;
        }
        return media.stream()
                .map(SamsaraSafetyEventMedia::url)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
