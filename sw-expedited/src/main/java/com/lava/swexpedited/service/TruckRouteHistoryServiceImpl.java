package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.truck.TruckRouteHistoryResponse;
import com.lava.swexpedited.truck.TruckRoutePoint;
import com.lava.swexpedited.truck.TruckRouteStop;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves {@code truckId}'s {@code matchedSamsaraVehicleId} from the already-synced {@link VektorTruckRepository} and
 * calls {@link SamsaraFleetClient#fetchVehicleGpsHistory} live, on every request - same on-demand, not-persisted
 * pattern as {@link SamsaraDriverLiveLocationServiceImpl}/{@link SamsaraDriverActivityServiceImpl}.
 *
 * <p>404 policy: {@link #findRouteHistory} returns empty only when {@code truckId} itself doesn't resolve to a
 * vektor_truck row (mirroring {@code TruckController.truck()}). A truck that exists but has no matched Samsara vehicle,
 * or no GPS history for the requested window, gets a present {@link TruckRouteHistoryResponse} with empty lists - a
 * day's route is inherently a "may legitimately be empty" collection, not a single fact that either exists or doesn't.
 */
@Service
@Transactional(readOnly = true)
public class TruckRouteHistoryServiceImpl implements TruckRouteHistoryService {

    /**
     * How close a stopped GPS sample must stay to its run's running centroid to still count as "the same stop" -
     * comparing against a fixed anchor point instead would let slow GPS drift over a multi-hour stop eventually exceed
     * this radius even though the truck never moved, so the centroid is recomputed incrementally instead (see
     * {@link #clusterStops}).
     */
    private static final double STOP_CLUSTER_RADIUS_METERS = 50.0;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /** Stops shorter than this are discarded - otherwise every stop sign/red light would get its own marker. */
    private static final long MIN_STOP_DURATION_MINUTES = 5;

    private static final double ECU_STOPPED_SPEED_MPH = 0.0;

    /** GPS-derived speed (isEcuSpeed=false) is noisier and may not hit an exact 0 while parked. */
    private static final double GPS_STOPPED_SPEED_MPH_THRESHOLD = 1.0;

    private final VektorTruckRepository vektorTruckRepository;
    private final SamsaraFleetClient samsaraFleetClient;

    public TruckRouteHistoryServiceImpl(
            VektorTruckRepository vektorTruckRepository, SamsaraFleetClient samsaraFleetClient) {
        this.vektorTruckRepository = vektorTruckRepository;
        this.samsaraFleetClient = samsaraFleetClient;
    }

    @Override
    public Optional<TruckRouteHistoryResponse> findRouteHistory(String truckId, Instant startTime, Instant endTime) {
        return vektorTruckRepository.findById(truckId).map(truck -> toResponse(truck, startTime, endTime));
    }

    private TruckRouteHistoryResponse toResponse(VektorTruckRow truck, Instant startTime, Instant endTime) {
        String vehicleId = truck.matchedSamsaraVehicleId();
        if (vehicleId == null) {
            return new TruckRouteHistoryResponse(List.of(), List.of());
        }

        List<VehicleStatsGps> gpsPoints =
                samsaraFleetClient.fetchVehicleGpsHistory(vehicleId, startTime, endTime).stream()
                        .sorted(Comparator.comparing(gps -> parseInstant(gps.getTime())))
                        .toList();

        List<TruckRoutePoint> points = gpsPoints.stream()
                .map(TruckRouteHistoryServiceImpl::toRoutePoint)
                .toList();
        List<TruckRouteStop> stops = clusterStops(gpsPoints);
        return new TruckRouteHistoryResponse(points, stops);
    }

    private static TruckRoutePoint toRoutePoint(VehicleStatsGps gps) {
        return new TruckRoutePoint(
                parseInstant(gps.getTime()),
                gps.getLatitude(),
                gps.getLongitude(),
                gps.getHeadingDegrees() != null ? (int) Math.round(gps.getHeadingDegrees()) : null,
                gps.getSpeedMilesPerHour());
    }

    /**
     * Walks the time-ordered samples, growing a "current run" of contiguous stopped samples (see {@link #isStopped})
     * whose location stays within {@link #STOP_CLUSTER_RADIUS_METERS} of the run's running centroid. The run closes
     * (and is emitted as a {@link TruckRouteStop}, unless shorter than {@link #MIN_STOP_DURATION_MINUTES}) as soon as a
     * sample is moving or falls outside that radius.
     */
    private List<TruckRouteStop> clusterStops(List<VehicleStatsGps> gpsPoints) {
        List<TruckRouteStop> stops = new ArrayList<>();
        List<VehicleStatsGps> currentRun = new ArrayList<>();
        double centroidLat = 0;
        double centroidLon = 0;

        for (VehicleStatsGps gps : gpsPoints) {
            if (!isStopped(gps)) {
                closeRun(currentRun).ifPresent(stops::add);
                currentRun.clear();
                continue;
            }
            if (currentRun.isEmpty()
                    || haversineMeters(centroidLat, centroidLon, gps.getLatitude(), gps.getLongitude())
                            <= STOP_CLUSTER_RADIUS_METERS) {
                currentRun.add(gps);
                int count = currentRun.size();
                centroidLat = ((centroidLat * (count - 1)) + gps.getLatitude()) / count;
                centroidLon = ((centroidLon * (count - 1)) + gps.getLongitude()) / count;
            } else {
                closeRun(currentRun).ifPresent(stops::add);
                currentRun.clear();
                currentRun.add(gps);
                centroidLat = gps.getLatitude();
                centroidLon = gps.getLongitude();
            }
        }
        closeRun(currentRun).ifPresent(stops::add);
        return stops;
    }

    private static boolean isStopped(VehicleStatsGps gps) {
        Double speed = gps.getSpeedMilesPerHour();
        if (speed == null) {
            return false;
        }
        boolean isEcuSpeed = Boolean.TRUE.equals(gps.getIsEcuSpeed());
        return isEcuSpeed ? speed == ECU_STOPPED_SPEED_MPH : speed <= GPS_STOPPED_SPEED_MPH_THRESHOLD;
    }

    private static Optional<TruckRouteStop> closeRun(List<VehicleStatsGps> run) {
        if (run.isEmpty()) {
            return Optional.empty();
        }
        Instant arrivalTime = parseInstant(run.getFirst().getTime());
        Instant departureTime = parseInstant(run.getLast().getTime());
        long stoppedMinutes = Duration.between(arrivalTime, departureTime).toMinutes();
        if (stoppedMinutes < MIN_STOP_DURATION_MINUTES) {
            return Optional.empty();
        }
        double centroidLat =
                run.stream().mapToDouble(VehicleStatsGps::getLatitude).average().orElseThrow();
        double centroidLon = run.stream()
                .mapToDouble(VehicleStatsGps::getLongitude)
                .average()
                .orElseThrow();
        String formattedLocation = mostCommonFormattedLocation(run);
        return Optional.of(new TruckRouteStop(
                centroidLat, centroidLon, formattedLocation, arrivalTime, departureTime, stoppedMinutes));
    }

    // formattedLocation is an advisory/secondary signal only (distance is primary - see clusterStops) since
    // reverse-geocoded addresses can flip near a boundary even while the truck is stationary, hence taking the run's
    // most frequent value rather than just its first sample's.
    private static @Nullable String mostCommonFormattedLocation(List<VehicleStatsGps> run) {
        return run.stream()
                .map(gps -> gps.getReverseGeo() != null ? gps.getReverseGeo().getFormattedLocation() : null)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.groupingBy(location -> location, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static Instant parseInstant(String rfc3339) {
        return OffsetDateTime.parse(rfc3339).toInstant();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
