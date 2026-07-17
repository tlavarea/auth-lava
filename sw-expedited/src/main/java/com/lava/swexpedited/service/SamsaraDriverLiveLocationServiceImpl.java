package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.samsara.SamsaraFleetClient;
import com.lava.swexpedited.repository.SamsaraDriverVehicleAssignmentRepository;
import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Resolves a driver's current vehicle assignment from the already-synced
 * {@link SamsaraDriverVehicleAssignmentRepository} and calls {@link SamsaraFleetClient#fetchVehicleLocation(String)}
 * live, on every request - deliberately not backed by samsara_vehicle_location, which only refreshes on
 * {@code SamsaraLocationSyncScheduler}'s ~1 min cadence. This on-demand call is scoped to a single vehicle and only
 * made while a driver's detail view is open, so it's cheap enough to skip the batch/persist pattern the rest of this
 * package uses.
 */
@Service
public class SamsaraDriverLiveLocationServiceImpl implements SamsaraDriverLiveLocationService {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository;

    public SamsaraDriverLiveLocationServiceImpl(
            SamsaraFleetClient samsaraFleetClient,
            SamsaraDriverVehicleAssignmentRepository samsaraDriverVehicleAssignmentRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraDriverVehicleAssignmentRepository = samsaraDriverVehicleAssignmentRepository;
    }

    @Override
    public Optional<DriverLiveLocationResponse> findLiveLocation(String driverId) {
        return samsaraDriverVehicleAssignmentRepository
                .findByDriverId(driverId)
                .map(SamsaraDriverVehicleAssignmentRow::vehicleId)
                .flatMap(this::fetchLiveLocation);
    }

    private Optional<DriverLiveLocationResponse> fetchLiveLocation(String vehicleId) {
        List<VehicleStatsResponseData> results = samsaraFleetClient.fetchVehicleLocation(vehicleId);
        return results.stream()
                .filter(payload -> payload.getGps() != null)
                .findFirst()
                .map(SamsaraDriverLiveLocationServiceImpl::toResponse);
    }

    private static DriverLiveLocationResponse toResponse(VehicleStatsResponseData payload) {
        VehicleStatsGps gps = payload.getGps();
        String formattedLocation =
                gps.getReverseGeo() != null ? gps.getReverseGeo().getFormattedLocation() : null;
        return new DriverLiveLocationResponse(
                toBigDecimal(gps.getLatitude()),
                toBigDecimal(gps.getLongitude()),
                toBigDecimal(gps.getHeadingDegrees()),
                toBigDecimal(gps.getSpeedMilesPerHour()),
                parseLocalDateTime(gps.getTime()),
                formattedLocation);
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static @Nullable LocalDateTime parseLocalDateTime(@Nullable String rfc3339) {
        return StringUtils.isNotBlank(rfc3339) ? OffsetDateTime.parse(rfc3339).toLocalDateTime() : null;
    }
}
