package com.lava.swexpedited.batch;

import com.lava.swexpedited.repository.SamsaraVehicleLocationRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import com.lava.swexpedited.samsara.model.VehicleStatsGps;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Fetches every vehicle's current GPS location and replaces samsara_vehicle_location - independent of
 * {@link SamsaraDriverSyncTasklet}'s tables (no FK either direction), refreshed on its own much faster (~1 min)
 * cadence. Vehicles with no location payload at all are skipped rather than inserted with nulls, since
 * latitude/longitude/location_time are NOT NULL columns (see 005-create-samsara-vehicle-location.yaml).
 */
@Component
@Slf4j
public class SamsaraLocationSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraVehicleLocationRepository samsaraVehicleLocationRepository;

    public SamsaraLocationSyncTasklet(
            SamsaraFleetClient samsaraFleetClient, SamsaraVehicleLocationRepository samsaraVehicleLocationRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraVehicleLocationRepository = samsaraVehicleLocationRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<SamsaraVehicleLocationRow> rows = samsaraFleetClient.fetchVehicleLocations().stream()
                .filter(payload -> payload.getGps() != null)
                .map(SamsaraLocationSyncTasklet::toRow)
                .toList();
        samsaraVehicleLocationRepository.replaceAll(rows);
        log.info("execute::stored {} samsara vehicle locations", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/vehicles/stats} entry (queried with {@code types=gps}) to a samsara_vehicle_location row.
     *
     * @param payload - the Samsara API response data as a {@link VehicleStatsResponseData} object.
     * @return the response data transformed to a {@link SamsaraVehicleLocationRow} object.
     */
    private static SamsaraVehicleLocationRow toRow(VehicleStatsResponseData payload) {
        VehicleStatsGps gps = payload.getGps();
        String formattedLocation =
                gps.getReverseGeo() != null ? gps.getReverseGeo().getFormattedLocation() : null;
        return new SamsaraVehicleLocationRow(
                payload.getId(),
                payload.getName(),
                toBigDecimal(gps.getLatitude()),
                toBigDecimal(gps.getLongitude()),
                toBigDecimal(gps.getHeadingDegrees()),
                toBigDecimal(gps.getSpeedMilesPerHour()),
                parseLocalDateTime(gps.getTime()),
                formattedLocation,
                null);
    }

    /**
     * Null-safe {@code Double} to {@code BigDecimal} conversion - latitude/longitude/heading/speed are all optional on
     * Samsara's payload but typed BigDecimal on the row/column side.
     *
     * @param value - the nullable Samsara API value as a {@link Double} object.
     * @return the value converted to a {@link BigDecimal} object, or null if the input was null.
     */
    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
