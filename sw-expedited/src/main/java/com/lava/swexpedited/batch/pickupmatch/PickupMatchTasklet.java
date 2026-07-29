package com.lava.swexpedited.batch.pickupmatch;

import com.lava.swexpedited.boot.autoconfigure.app.PickupMatchProperties;
import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Flags shipment_listing rows {@code viable_pickup = true} when a Vektor-tracked driver is within
 * {@code pickup-match.max-distance-miles} of the shipment's pickup location (via {@link RouteMatrixClient}'s driving
 * distance) and can actually drive there - {@code eta} at their current destination plus {@link RouteMatrixClient}'s
 * computed driving duration plus {@code pickup-match.arrival-buffer} slack for HOS-mandated breaks - before the
 * shipment's GFM-quoted pickup window (see {@link PickupWindowMapper}) closes. {@code pickup-match.time-window} is a
 * separate, coarser concern: a cheap in-memory pre-filter on raw {@code eta} vs. the pickup window, used only to decide
 * which pairs are worth spending a route matrix lookup on before driving duration is known - not the final viability
 * check. Runs as its own {@code pickupMatchJob} (see {@code PickupMatchJobConfig}), on a schedule offset after
 * {@code shipmentSyncJob} rather than chained onto it - it depends on {@code shipment_detail.raw_response} (populated
 * by that job's {@code shipmentDetailStep}) for the precise pickup window, so it re-reads that table fresh from
 * Postgres rather than relying on an in-memory handoff within a single job execution; see
 * 009-add-viable-pickup-to-shipment-listing.yaml for why this writes a plain column rather than a separate derived
 * table.
 *
 * <p>Time-filters first, in memory, before calling {@link RouteMatrixClient} at all: only shipments/manifests with at
 * least one time-compatible counterpart are sent to Google, to keep the (paid) route matrix request small. The
 * remaining, still-full cross product of that reduced set is sent as one matrix (rather than the exact sparse set of
 * time-compatible pairs, which {@code computeRouteMatrix} isn't shaped for) - acceptable waste given this app's current
 * shipment/manifest volume, revisit if either grows enough to matter.
 *
 * <p>A single tasklet, not chunked - same reasoning as {@code VektorSyncTasklet}: a handful of bulk reads plus a small,
 * pre-filtered set of batched {@link RouteMatrixClient} calls, not one HTTP call per shipment.
 */
@Component
@Slf4j
public class PickupMatchTasklet implements Tasklet {

    private static final ZoneId GFM_ZONE = ZoneId.of("America/New_York");

    private final ShipmentListingRepository shipmentListingRepository;
    private final ShipmentDetailRepository shipmentDetailRepository;
    private final VektorManifestRepository vektorManifestRepository;
    private final PickupWindowMapper pickupWindowMapper;
    private final RouteMatrixClient routeMatrixClient;
    private final PickupMatchProperties pickupMatchProperties;

    public PickupMatchTasklet(
            ShipmentListingRepository shipmentListingRepository,
            ShipmentDetailRepository shipmentDetailRepository,
            VektorManifestRepository vektorManifestRepository,
            PickupWindowMapper pickupWindowMapper,
            RouteMatrixClient routeMatrixClient,
            PickupMatchProperties pickupMatchProperties) {
        this.shipmentListingRepository = shipmentListingRepository;
        this.shipmentDetailRepository = shipmentDetailRepository;
        this.vektorManifestRepository = vektorManifestRepository;
        this.pickupWindowMapper = pickupWindowMapper;
        this.routeMatrixClient = routeMatrixClient;
        this.pickupMatchProperties = pickupMatchProperties;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Duration timeWindow = this.pickupMatchProperties.timeWindow();
        Map<Long, PickupWindow> windowsByOfferId = windowsByOfferId();

        List<ShipmentWithWindow> shipmentsWithWindow = this.shipmentListingRepository.findAll().stream()
                .map(shipment -> {
                    PickupWindow window = windowsByOfferId.get(shipment.offerId());
                    return window == null ? null : new ShipmentWithWindow(shipment, window);
                })
                .filter(Objects::nonNull)
                .filter(sw -> sw.window().latest().isAfter(LocalDateTime.now(GFM_ZONE)))
                .toList();

        List<VektorManifestRow> manifests = this.vektorManifestRepository.findAll().stream()
                .filter(manifest -> manifest.eta() != null
                        && manifest.destinationLatitude() != null
                        && manifest.destinationLongitude() != null)
                .toList();

        List<ShipmentWithWindow> reducedShipments = shipmentsWithWindow.stream()
                .filter(sw -> manifests.stream().anyMatch(m -> withinTimeWindow(sw.window(), m.eta(), timeWindow)))
                .toList();
        List<VektorManifestRow> reducedManifests = manifests.stream()
                .filter(m ->
                        shipmentsWithWindow.stream().anyMatch(sw -> withinTimeWindow(sw.window(), m.eta(), timeWindow)))
                .toList();

        if (reducedShipments.isEmpty() || reducedManifests.isEmpty()) {
            log.info("execute::no time-window candidates, skipping route matrix lookup");
            return RepeatStatus.FINISHED;
        }

        List<String> originAddresses =
                reducedShipments.stream().map(sw -> sw.shipment().origin()).toList();
        List<RouteMatrixClient.LatLng> destinations = reducedManifests.stream()
                .map(m -> new RouteMatrixClient.LatLng(m.destinationLatitude(), m.destinationLongitude()))
                .toList();
        List<RouteMatrixClient.RouteMatrixElement> elements =
                this.routeMatrixClient.computeRouteMatrix(originAddresses, destinations);

        Map<Integer, Map<Integer, RouteMatrixClient.RouteMatrixElement>> elementsByIndex = new HashMap<>();
        for (RouteMatrixClient.RouteMatrixElement element : elements) {
            elementsByIndex
                    .computeIfAbsent(element.originIndex(), k -> new HashMap<>())
                    .put(element.destinationIndex(), element);
        }

        BigDecimal maxDistanceMiles = BigDecimal.valueOf(this.pickupMatchProperties.maxDistanceMiles());
        Duration arrivalBuffer = this.pickupMatchProperties.arrivalBuffer();
        Set<Long> viableOfferIds = new HashSet<>();
        for (int s = 0; s < reducedShipments.size(); s++) {
            ShipmentWithWindow sw = reducedShipments.get(s);
            for (int m = 0; m < reducedManifests.size(); m++) {
                RouteMatrixClient.RouteMatrixElement element =
                        elementsByIndex.getOrDefault(s, Map.of()).get(m);
                if (element != null
                        && element.routeExists()
                        && element.distanceMiles().compareTo(maxDistanceMiles) <= 0
                        && canReachPickupBeforeClose(
                                sw.window(), reducedManifests.get(m).eta(), element.durationValue(), arrivalBuffer)) {
                    viableOfferIds.add(sw.shipment().offerId());
                    break;
                }
            }
        }

        this.shipmentListingRepository.markViablePickups(viableOfferIds);
        log.info(
                "execute::flagged {} of {} time-candidate shipments as viable pickups",
                viableOfferIds.size(),
                reducedShipments.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Parses every listed shipment's pickup window up front rather than per-candidate, since {@link PickupWindowMapper}
     * has to reparse the full {@code raw_response} JSON each time - cheaper to pay that cost once per shipment than
     * repeatedly inside the pairwise time-filtering below.
     */
    private Map<Long, PickupWindow> windowsByOfferId() {
        Map<Long, PickupWindow> windows = new HashMap<>();
        for (ShipmentDetailRow detail : this.shipmentDetailRepository.findAll()) {
            PickupWindow window = this.pickupWindowMapper.map(detail.rawResponse());
            if (window != null) {
                windows.put(detail.offerId(), window);
            }
        }
        return windows;
    }

    private boolean withinTimeWindow(PickupWindow window, LocalDateTime eta, Duration timeWindow) {
        LocalDateTime earliestAllowed = window.earliest().minus(timeWindow);
        LocalDateTime latestAllowed = window.latest().plus(timeWindow);
        return !eta.isBefore(earliestAllowed) && !eta.isAfter(latestAllowed);
    }

    /**
     * The actual per-pair feasibility check: {@code eta} is when the driver becomes free at their current destination,
     * so they can't reach the pickup until {@code eta + driveDuration}, and {@code arrivalBuffer} pads that further for
     * HOS-mandated breaks the raw driving duration doesn't account for. {@code driveDuration} is null when
     * {@link RouteMatrixClient.RouteMatrixElement#routeExists()} is false, which callers already exclude before
     * reaching here - never called with a null duration in practice, but treated as infeasible rather than throwing,
     * since a missing duration from Google means this app can't establish feasibility either way.
     */
    private boolean canReachPickupBeforeClose(
            PickupWindow window, LocalDateTime eta, @Nullable Duration driveDuration, Duration arrivalBuffer) {
        if (driveDuration == null) {
            return false;
        }
        LocalDateTime pickupArrival = eta.plus(driveDuration).plus(arrivalBuffer);
        return !pickupArrival.isAfter(window.latest());
    }

    private record ShipmentWithWindow(ShipmentListingRow shipment, PickupWindow window) {}
}
