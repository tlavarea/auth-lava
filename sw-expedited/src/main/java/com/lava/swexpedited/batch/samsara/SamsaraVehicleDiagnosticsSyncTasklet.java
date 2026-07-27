package com.lava.swexpedited.batch.samsara;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.repository.SamsaraVehicleDiagnosticsRepository;
import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import com.lava.swexpedited.samsara.model.VehicleStatsResponseData;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Fetches every vehicle's diagnostic snapshot (fuel, odometer, engine hours, fault codes, engine state, DEF level,
 * battery voltage, coolant temp, RPM, engine load) and replaces samsara_vehicle_diagnostics - independent of
 * {@link SamsaraVehicleSyncTasklet}/{@link SamsaraLocationSyncTasklet}'s tables (no FK either direction), refreshed on
 * its own cadence. Unlike {@link SamsaraLocationSyncTasklet}, no vehicle is filtered out here - every column is
 * individually nullable, so a vehicle reporting only some of the 10 stat types still gets a row.
 */
@Component
@Slf4j
public class SamsaraVehicleDiagnosticsSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SamsaraVehicleDiagnosticsSyncTasklet(
            SamsaraFleetClient samsaraFleetClient,
            SamsaraVehicleDiagnosticsRepository samsaraVehicleDiagnosticsRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraVehicleDiagnosticsRepository = samsaraVehicleDiagnosticsRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<SamsaraVehicleDiagnosticsRow> rows = this.samsaraFleetClient.fetchVehicleDiagnostics().stream()
                .map(this::toRow)
                .toList();
        this.samsaraVehicleDiagnosticsRepository.replaceAll(rows);
        log.info("execute::stored {} samsara vehicle diagnostics", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one merged {@code /fleet/vehicles/stats} entry (see {@link SamsaraFleetClient#fetchVehicleDiagnostics()}) to
     * a samsara_vehicle_diagnostics row, in Samsara's native units - unit conversion for display happens in
     * {@code TruckServiceImpl}, not here.
     *
     * @param payload - the merged Samsara API response data as a {@link VehicleStatsResponseData} object.
     * @return the response data transformed to a {@link SamsaraVehicleDiagnosticsRow} object.
     */
    private SamsaraVehicleDiagnosticsRow toRow(VehicleStatsResponseData payload) {
        return new SamsaraVehicleDiagnosticsRow(
                payload.getId(),
                payload.getFuelPercent() != null
                        ? payload.getFuelPercent().getValue().intValue()
                        : null,
                payload.getObdOdometerMeters() != null
                        ? payload.getObdOdometerMeters().getValue()
                        : null,
                payload.getObdEngineSeconds() != null
                        ? payload.getObdEngineSeconds().getValue()
                        : null,
                writeFaultCodesAsJson(payload.getFaultCodes()),
                payload.getEngineState() != null && payload.getEngineState().getValue() != null
                        ? payload.getEngineState().getValue().getValue()
                        : null,
                payload.getDefLevelMilliPercent() != null
                        ? payload.getDefLevelMilliPercent().getValue().intValue()
                        : null,
                payload.getBatteryMilliVolts() != null
                        ? payload.getBatteryMilliVolts().getValue().intValue()
                        : null,
                payload.getEngineCoolantTemperatureMilliC() != null
                        ? payload.getEngineCoolantTemperatureMilliC().getValue().intValue()
                        : null,
                payload.getEngineRpm() != null
                        ? payload.getEngineRpm().getValue().intValue()
                        : null,
                payload.getEngineLoadPercent() != null
                        ? payload.getEngineLoadPercent().getValue().intValue()
                        : null,
                null);
    }

    private @Nullable String writeFaultCodesAsJson(@Nullable Object faultCodes) {
        if (faultCodes == null) {
            return null;
        }
        try {
            return this.objectMapper.writeValueAsString(faultCodes);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Samsara vehicle faultCodes payload", e);
        }
    }
}
