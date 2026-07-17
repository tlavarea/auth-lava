package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_MANIFEST;

import com.lava.swexpedited.model.database.tables.pojos.VektorManifest;
import com.lava.swexpedited.model.database.tables.records.VektorManifestRecord;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep14;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VektorManifestRepositoryImpl implements VektorManifestRepository {

    private final DSLContext dsl;

    public VektorManifestRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<VektorManifestRow> rows) {
        this.dsl.deleteFrom(VEKTOR_MANIFEST).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep14<
                        VektorManifestRecord,
                        Long,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        BigDecimal,
                        BigDecimal,
                        LocalDateTime,
                        String,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        VEKTOR_MANIFEST,
                        VEKTOR_MANIFEST.MANIFEST_NUMBER,
                        VEKTOR_MANIFEST.MANIFEST_ID,
                        VEKTOR_MANIFEST.DRIVER_ID,
                        VEKTOR_MANIFEST.DRIVER_NAME,
                        VEKTOR_MANIFEST.MATCHED_SAMSARA_DRIVER_ID,
                        VEKTOR_MANIFEST.STATUS,
                        VEKTOR_MANIFEST.ORIGIN,
                        VEKTOR_MANIFEST.DESTINATION,
                        VEKTOR_MANIFEST.DESTINATION_LATITUDE,
                        VEKTOR_MANIFEST.DESTINATION_LONGITUDE,
                        VEKTOR_MANIFEST.ETA,
                        VEKTOR_MANIFEST.LOAD_REFERENCE,
                        VEKTOR_MANIFEST.RAW_RESPONSE,
                        VEKTOR_MANIFEST.SYNCED_AT);

        for (VektorManifestRow row : rows) {
            insert.values(
                    row.manifestNumber(),
                    row.manifestId(),
                    row.driverId(),
                    row.driverName(),
                    row.matchedSamsaraDriverId(),
                    row.status(),
                    row.origin(),
                    row.destination(),
                    row.destinationLatitude(),
                    row.destinationLongitude(),
                    row.eta(),
                    row.loadReference(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<VektorManifestRow> findAll() {
        return this.dsl.selectFrom(VEKTOR_MANIFEST).fetchInto(VektorManifest.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<VektorManifestRow> findByManifestNumber(long manifestNumber) {
        return this.dsl
                .selectFrom(VEKTOR_MANIFEST)
                .where(VEKTOR_MANIFEST.MANIFEST_NUMBER.eq(manifestNumber))
                .fetchOptionalInto(VektorManifest.class)
                .map(this::toRow);
    }

    private VektorManifestRow toRow(VektorManifest row) {
        return new VektorManifestRow(
                row.manifestNumber(),
                row.manifestId(),
                row.driverId(),
                row.driverName(),
                row.matchedSamsaraDriverId(),
                row.status(),
                row.origin(),
                row.destination(),
                row.destinationLatitude(),
                row.destinationLongitude(),
                row.eta(),
                row.loadReference(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
