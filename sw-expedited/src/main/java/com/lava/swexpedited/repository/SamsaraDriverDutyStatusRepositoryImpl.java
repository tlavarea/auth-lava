package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_DRIVER_DUTY_STATUS;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraDriverDutyStatus;
import com.lava.swexpedited.model.database.tables.records.SamsaraDriverDutyStatusRecord;
import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraDriverDutyStatusRepositoryImpl implements SamsaraDriverDutyStatusRepository {

    private final DSLContext dsl;

    public SamsaraDriverDutyStatusRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraDriverDutyStatusRow> rows) {
        this.dsl.deleteFrom(SAMSARA_DRIVER_DUTY_STATUS).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep8<
                        SamsaraDriverDutyStatusRecord,
                        String,
                        String,
                        Long,
                        Long,
                        Long,
                        Long,
                        LocalDateTime,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_DRIVER_DUTY_STATUS,
                        SAMSARA_DRIVER_DUTY_STATUS.DRIVER_ID,
                        SAMSARA_DRIVER_DUTY_STATUS.DUTY_STATUS,
                        SAMSARA_DRIVER_DUTY_STATUS.DRIVE_REMAINING_DURATION_MS,
                        SAMSARA_DRIVER_DUTY_STATUS.SHIFT_REMAINING_DURATION_MS,
                        SAMSARA_DRIVER_DUTY_STATUS.CYCLE_REMAINING_DURATION_MS,
                        SAMSARA_DRIVER_DUTY_STATUS.TIME_UNTIL_BREAK_DURATION_MS,
                        SAMSARA_DRIVER_DUTY_STATUS.DUTY_STATUS_SINCE,
                        SAMSARA_DRIVER_DUTY_STATUS.SYNCED_AT);

        for (SamsaraDriverDutyStatusRow row : rows) {
            insert.values(
                    row.driverId(),
                    row.dutyStatus(),
                    row.driveRemainingDurationMs(),
                    row.shiftRemainingDurationMs(),
                    row.cycleRemainingDurationMs(),
                    row.timeUntilBreakDurationMs(),
                    row.dutyStatusSince(),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraDriverDutyStatusRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_DRIVER_DUTY_STATUS).fetchInto(SamsaraDriverDutyStatus.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraDriverDutyStatusRow> findByDriverId(String driverId) {
        return this.dsl
                .selectFrom(SAMSARA_DRIVER_DUTY_STATUS)
                .where(SAMSARA_DRIVER_DUTY_STATUS.DRIVER_ID.eq(driverId))
                .fetchOptionalInto(SamsaraDriverDutyStatus.class)
                .map(this::toRow);
    }

    private SamsaraDriverDutyStatusRow toRow(SamsaraDriverDutyStatus row) {
        return new SamsaraDriverDutyStatusRow(
                row.driverId(),
                row.dutyStatus(),
                row.driveRemainingDurationMs(),
                row.shiftRemainingDurationMs(),
                row.cycleRemainingDurationMs(),
                row.timeUntilBreakDurationMs(),
                row.dutyStatusSince(),
                row.syncedAt());
    }
}
