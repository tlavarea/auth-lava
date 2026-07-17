package com.lava.swexpedited.batch.samsara;

import com.lava.swexpedited.repository.SamsaraDriverDutyStatusRepository;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.model.HosClocks;
import com.lava.swexpedited.samsara.model.HosClocksForDriver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        List<String> driverIds = this.samsaraDriverRepository.findAll().stream()
                .map(SamsaraDriverRow::id)
                .toList();

        Map<String, SamsaraDriverDutyStatusRow> previousByDriverId =
                this.samsaraDriverDutyStatusRepository.findAll().stream()
                        .collect(Collectors.toMap(SamsaraDriverDutyStatusRow::driverId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();

        List<SamsaraDriverDutyStatusRow> rows = this.samsaraFleetClient.fetchDriverDutyStatuses(driverIds).stream()
                .filter(hosClocksForDriver -> hosClocksForDriver.getDriver() != null)
                .map(hosClocksForDriver -> toRow(hosClocksForDriver, previousByDriverId, now))
                .toList();
        this.samsaraDriverDutyStatusRepository.replaceAll(rows);
        log.info("execute::stored {} samsara driver duty statuses", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/hos/clocks} entry to a samsara_driver_duty_status row.
     *
     * @param hosClocksForDriver - the Samsara API response entry as a {@link HosClocksForDriver} object.
     * @param previousByDriverId - the table's contents from before this sync, keyed by driver id, used to derive
     *     dutyStatusSince (see below).
     * @param now - this sync's timestamp, reused as dutyStatusSince for any driver whose dutyStatus is new or changed.
     * @return the response entry transformed to a {@link SamsaraDriverDutyStatusRow} object.
     */
    private static SamsaraDriverDutyStatusRow toRow(
            HosClocksForDriver hosClocksForDriver,
            Map<String, SamsaraDriverDutyStatusRow> previousByDriverId,
            LocalDateTime now) {
        String driverId = hosClocksForDriver.getDriver().getId();
        String hosStatusType = hosClocksForDriver.getCurrentDutyStatus() != null
                ? hosClocksForDriver.getCurrentDutyStatus().getHosStatusType()
                : null;
        // Samsara sends "" rather than omitting hosStatusType when a driver's app is disconnected - blank is stored
        // as null, not a literal empty string (see CurrentDutyStatus's javadoc in the vendored samsara-api.json).
        String dutyStatus = StringUtils.isNotBlank(hosStatusType) ? hosStatusType : null;
        HosClocks clocks = hosClocksForDriver.getClocks();
        Long driveRemainingDurationMs =
                clocks != null && clocks.getDrive() != null ? clocks.getDrive().getDriveRemainingDurationMs() : null;
        Long shiftRemainingDurationMs =
                clocks != null && clocks.getShift() != null ? clocks.getShift().getShiftRemainingDurationMs() : null;
        Long cycleRemainingDurationMs =
                clocks != null && clocks.getCycle() != null ? clocks.getCycle().getCycleRemainingDurationMs() : null;
        Long timeUntilBreakDurationMs =
                clocks != null && clocks.getBreak() != null ? clocks.getBreak().getTimeUntilBreakDurationMs() : null;

        // Samsara's response has no "since" timestamp for the current duty status - carry the previous sync's value
        // forward when dutyStatus hasn't changed, otherwise this driver just transitioned (or is new), so "now" is
        // the start of their current status.
        SamsaraDriverDutyStatusRow previous = previousByDriverId.get(driverId);
        LocalDateTime dutyStatusSince = previous != null && Objects.equals(previous.dutyStatus(), dutyStatus)
                ? previous.dutyStatusSince()
                : now;

        return new SamsaraDriverDutyStatusRow(
                driverId,
                dutyStatus,
                driveRemainingDurationMs,
                shiftRemainingDurationMs,
                cycleRemainingDurationMs,
                timeUntilBreakDurationMs,
                dutyStatusSince,
                null);
    }
}
