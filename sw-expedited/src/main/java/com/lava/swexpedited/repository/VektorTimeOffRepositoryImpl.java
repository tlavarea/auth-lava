package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_TIME_OFF;
import static org.jooq.impl.DSL.excluded;

import com.lava.swexpedited.model.database.tables.pojos.VektorTimeOff;
import com.lava.swexpedited.model.database.tables.records.VektorTimeOffRecord;
import com.lava.swexpedited.vektor.VektorTimeOffRow;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VektorTimeOffRepositoryImpl implements VektorTimeOffRepository {

    private final DSLContext dsl;

    public VektorTimeOffRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void upsertAll(List<VektorTimeOffRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep8<
                        VektorTimeOffRecord,
                        String,
                        String,
                        String,
                        LocalDateTime,
                        LocalDateTime,
                        String,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        VEKTOR_TIME_OFF,
                        VEKTOR_TIME_OFF.ID,
                        VEKTOR_TIME_OFF.TRUCK_ID,
                        VEKTOR_TIME_OFF.MATCHED_SAMSARA_DRIVER_ID,
                        VEKTOR_TIME_OFF.START_AT,
                        VEKTOR_TIME_OFF.END_AT,
                        VEKTOR_TIME_OFF.REASON,
                        VEKTOR_TIME_OFF.RAW_RESPONSE,
                        VEKTOR_TIME_OFF.SYNCED_AT);

        for (VektorTimeOffRow row : rows) {
            insert.values(
                    row.id(),
                    row.truckId(),
                    row.matchedSamsaraDriverId(),
                    row.startAt(),
                    row.endAt(),
                    row.reason(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.onConflict(VEKTOR_TIME_OFF.ID)
                .doUpdate()
                .set(VEKTOR_TIME_OFF.TRUCK_ID, excluded(VEKTOR_TIME_OFF.TRUCK_ID))
                .set(VEKTOR_TIME_OFF.MATCHED_SAMSARA_DRIVER_ID, excluded(VEKTOR_TIME_OFF.MATCHED_SAMSARA_DRIVER_ID))
                .set(VEKTOR_TIME_OFF.START_AT, excluded(VEKTOR_TIME_OFF.START_AT))
                .set(VEKTOR_TIME_OFF.END_AT, excluded(VEKTOR_TIME_OFF.END_AT))
                .set(VEKTOR_TIME_OFF.REASON, excluded(VEKTOR_TIME_OFF.REASON))
                .set(VEKTOR_TIME_OFF.RAW_RESPONSE, excluded(VEKTOR_TIME_OFF.RAW_RESPONSE))
                .set(VEKTOR_TIME_OFF.SYNCED_AT, excluded(VEKTOR_TIME_OFF.SYNCED_AT))
                .execute();
    }

    @Override
    public List<VektorTimeOffRow> findByWindow(LocalDateTime windowStart, LocalDateTime windowEnd) {
        return this.dsl
                .selectFrom(VEKTOR_TIME_OFF)
                .where(VEKTOR_TIME_OFF.START_AT.lt(windowEnd))
                .and(VEKTOR_TIME_OFF.END_AT.gt(windowStart))
                .fetchInto(VektorTimeOff.class)
                .stream()
                .map(this::toRow)
                .toList();
    }

    private VektorTimeOffRow toRow(VektorTimeOff row) {
        return new VektorTimeOffRow(
                row.id(),
                row.truckId(),
                row.matchedSamsaraDriverId(),
                row.startAt(),
                row.endAt(),
                row.reason(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
