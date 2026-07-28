package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_TRAILER;

import com.lava.swexpedited.model.database.tables.pojos.VektorTrailer;
import com.lava.swexpedited.model.database.tables.records.VektorTrailerRecord;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VektorTrailerRepositoryImpl implements VektorTrailerRepository {

    private final DSLContext dsl;

    public VektorTrailerRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<VektorTrailerRow> rows) {
        this.dsl.deleteFrom(VEKTOR_TRAILER).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep8<VektorTrailerRecord, String, String, String, Integer, String, JSON, LocalDateTime, String>
                insert = this.dsl.insertInto(
                        VEKTOR_TRAILER,
                        VEKTOR_TRAILER.ID,
                        VEKTOR_TRAILER.LABEL,
                        VEKTOR_TRAILER.MANUFACTURER,
                        VEKTOR_TRAILER.YEAR,
                        VEKTOR_TRAILER.VIN,
                        VEKTOR_TRAILER.RAW_RESPONSE,
                        VEKTOR_TRAILER.SYNCED_AT,
                        VEKTOR_TRAILER.MATCHED_SAMSARA_TRAILER_ID);

        for (VektorTrailerRow row : rows) {
            insert.values(
                    row.id(),
                    row.label(),
                    row.manufacturer(),
                    row.year(),
                    row.vin(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt,
                    row.matchedSamsaraTrailerId());
        }

        insert.execute();
    }

    @Override
    public List<VektorTrailerRow> findAll() {
        return this.dsl.selectFrom(VEKTOR_TRAILER).fetchInto(VektorTrailer.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<VektorTrailerRow> findById(String id) {
        return this.dsl
                .selectFrom(VEKTOR_TRAILER)
                .where(VEKTOR_TRAILER.ID.eq(id))
                .fetchOptionalInto(VektorTrailer.class)
                .map(this::toRow);
    }

    private VektorTrailerRow toRow(VektorTrailer row) {
        return new VektorTrailerRow(
                row.id(),
                row.label(),
                row.manufacturer(),
                row.year(),
                row.vin(),
                row.rawResponse().data(),
                row.syncedAt(),
                row.matchedSamsaraTrailerId());
    }
}
