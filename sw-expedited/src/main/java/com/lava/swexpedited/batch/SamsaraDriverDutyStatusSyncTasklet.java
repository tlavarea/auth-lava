package com.lava.swexpedited.batch;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Fetches every currently-synced driver's HOS duty status and replaces samsara_driver_duty_status - independent of
 * {@link SamsaraDriverSyncTasklet}'s tables (no FK either direction), refreshed on its own much faster (~1 min)
 * cadence, same shape as {@link SamsaraLocationSyncTasklet}. Scoped to {@link SamsaraDriverRepository}'s already-synced
 * roster rather than issuing a fresh {@code /fleet/drivers} call, since this tasklet only needs the id list, not a full
 * re-sync.
 */
@Component
@Slf4j
public class SamsaraDriverDutyStatusSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository;

    public SamsaraDriverDutyStatusSyncTasklet(
            SamsaraFleetClient samsaraFleetClient,
            SamsaraDriverRepository samsaraDriverRepository,
            SamsaraDriverDutyStatusRepository samsaraDriverDutyStatusRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.samsaraDriverDutyStatusRepository = samsaraDriverDutyStatusRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<String> driverIds = samsaraDriverRepository.findAll().stream()
                .map(SamsaraDriverRow::id)
                .toList();

        List<SamsaraDriverDutyStatusRow> rows = samsaraFleetClient.fetchDriverDutyStatuses(driverIds).stream()
                .filter(hosClocksForDriver -> hosClocksForDriver.getDriver() != null)
                .map(SamsaraDriverDutyStatusSyncTasklet::toRow)
                .toList();
        samsaraDriverDutyStatusRepository.replaceAll(rows);
        log.info("execute::stored {} samsara driver duty statuses", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/hos/clocks} entry to a samsara_driver_duty_status row.
     *
     * @param hosClocksForDriver - the Samsara API response entry as a {@link HosClocksForDriver} object.
     * @return the response entry transformed to a {@link SamsaraDriverDutyStatusRow} object.
     */
    private static SamsaraDriverDutyStatusRow toRow(HosClocksForDriver hosClocksForDriver) {
        String hosStatusType = hosClocksForDriver.getCurrentDutyStatus() != null
                ? hosClocksForDriver.getCurrentDutyStatus().getHosStatusType()
                : null;
        // Samsara sends "" rather than omitting hosStatusType when a driver's app is disconnected - blank is stored
        // as null, not a literal empty string (see CurrentDutyStatus's javadoc in the vendored samsara-api.json).
        String dutyStatus = StringUtils.isNotBlank(hosStatusType) ? hosStatusType : null;
        return new SamsaraDriverDutyStatusRow(hosClocksForDriver.getDriver().getId(), dutyStatus, null);
    }
}
