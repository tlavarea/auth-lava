package com.lava.swexpedited.batch.samsara;

import com.lava.swexpedited.repository.SamsaraVehicleRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import com.lava.swexpedited.samsara.SamsaraVehicleWithRaw;
import com.lava.swexpedited.samsara.model.Vehicle;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Fetches Samsara's full vehicle roster and replaces samsara_vehicle. Exists so {@code VinMatchingTruckMatchStrategy}
 * has a vin -&gt; Samsara vehicle id lookup to match against vektor_truck.vin - independent of
 * {@link SamsaraDriverSyncTasklet}/{@link SamsaraLocationSyncTasklet}'s tables (no FK either direction).
 */
@Component
@Slf4j
public class SamsaraVehicleSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraVehicleRepository samsaraVehicleRepository;

    public SamsaraVehicleSyncTasklet(
            SamsaraFleetClient samsaraFleetClient, SamsaraVehicleRepository samsaraVehicleRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraVehicleRepository = samsaraVehicleRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<SamsaraVehicleRow> rows = this.samsaraFleetClient.fetchVehicles().stream()
                .map(SamsaraVehicleSyncTasklet::toRow)
                .toList();
        this.samsaraVehicleRepository.replaceAll(rows);
        log.info("execute::stored {} samsara vehicles", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/vehicles} entry, paired with its captured raw JSON, to a samsara_vehicle row.
     *
     * @param vehicleWithRaw - the Samsara vehicle payload and its captured raw JSON as a {@link SamsaraVehicleWithRaw}
     *     object.
     * @return the vehicle data transformed to a {@link SamsaraVehicleRow} object.
     */
    private static SamsaraVehicleRow toRow(SamsaraVehicleWithRaw vehicleWithRaw) {
        Vehicle payload = vehicleWithRaw.payload();
        return new SamsaraVehicleRow(
                payload.getId(),
                payload.getVin(),
                payload.getName(),
                payload.getMake(),
                payload.getModel(),
                payload.getYear(),
                payload.getLicensePlate(),
                vehicleWithRaw.rawJson(),
                null);
    }
}
