package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_TRAILER;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraTrailer;
import com.lava.swexpedited.model.database.tables.records.SamsaraTrailerRecord;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep7;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraTrailerRepositoryImpl implements SamsaraTrailerRepository {

    private final DSLContext dsl;

    public SamsaraTrailerRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraTrailerRow> rows) {
        this.dsl.deleteFrom(SAMSARA_TRAILER).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep7<SamsaraTrailerRecord, String, String, String, String, String, JSON, LocalDateTime> insert =
                this.dsl.insertInto(
                        SAMSARA_TRAILER,
                        SAMSARA_TRAILER.ID,
                        SAMSARA_TRAILER.VIN,
                        SAMSARA_TRAILER.NAME,
                        SAMSARA_TRAILER.LICENSE_PLATE,
                        SAMSARA_TRAILER.TRAILER_SERIAL_NUMBER,
                        SAMSARA_TRAILER.RAW_RESPONSE,
                        SAMSARA_TRAILER.SYNCED_AT);

        for (SamsaraTrailerRow row : rows) {
            insert.values(
                    row.id(),
                    row.vin(),
                    row.name(),
                    row.licensePlate(),
                    row.trailerSerialNumber(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraTrailerRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_TRAILER).fetchInto(SamsaraTrailer.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraTrailerRow> findById(String id) {
        return this.dsl
                .selectFrom(SAMSARA_TRAILER)
                .where(SAMSARA_TRAILER.ID.eq(id))
                .fetchOptionalInto(SamsaraTrailer.class)
                .map(this::toRow);
    }

    private SamsaraTrailerRow toRow(SamsaraTrailer row) {
        return new SamsaraTrailerRow(
                row.id(),
                row.vin(),
                row.name(),
                row.licensePlate(),
                row.trailerSerialNumber(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
