package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_MANIFEST;
import static org.jooq.impl.DSL.excluded;

import com.lava.swexpedited.model.database.tables.pojos.VektorManifest;
import com.lava.swexpedited.model.database.tables.records.VektorManifestRecord;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep15;
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
    public void upsertAll(List<VektorManifestRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep15<
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
                        VEKTOR_MANIFEST.PICKUP_APPOINTMENT_START,
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
                    row.pickupAppointmentStart(),
                    row.eta(),
                    row.loadReference(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.onConflict(VEKTOR_MANIFEST.MANIFEST_NUMBER)
                .doUpdate()
                .set(VEKTOR_MANIFEST.MANIFEST_ID, excluded(VEKTOR_MANIFEST.MANIFEST_ID))
                .set(VEKTOR_MANIFEST.DRIVER_ID, excluded(VEKTOR_MANIFEST.DRIVER_ID))
                .set(VEKTOR_MANIFEST.DRIVER_NAME, excluded(VEKTOR_MANIFEST.DRIVER_NAME))
                .set(VEKTOR_MANIFEST.MATCHED_SAMSARA_DRIVER_ID, excluded(VEKTOR_MANIFEST.MATCHED_SAMSARA_DRIVER_ID))
                .set(VEKTOR_MANIFEST.STATUS, excluded(VEKTOR_MANIFEST.STATUS))
                .set(VEKTOR_MANIFEST.ORIGIN, excluded(VEKTOR_MANIFEST.ORIGIN))
                .set(VEKTOR_MANIFEST.DESTINATION, excluded(VEKTOR_MANIFEST.DESTINATION))
                .set(VEKTOR_MANIFEST.DESTINATION_LATITUDE, excluded(VEKTOR_MANIFEST.DESTINATION_LATITUDE))
                .set(VEKTOR_MANIFEST.DESTINATION_LONGITUDE, excluded(VEKTOR_MANIFEST.DESTINATION_LONGITUDE))
                .set(VEKTOR_MANIFEST.PICKUP_APPOINTMENT_START, excluded(VEKTOR_MANIFEST.PICKUP_APPOINTMENT_START))
                .set(VEKTOR_MANIFEST.ETA, excluded(VEKTOR_MANIFEST.ETA))
                .set(VEKTOR_MANIFEST.LOAD_REFERENCE, excluded(VEKTOR_MANIFEST.LOAD_REFERENCE))
                .set(VEKTOR_MANIFEST.RAW_RESPONSE, excluded(VEKTOR_MANIFEST.RAW_RESPONSE))
                .set(VEKTOR_MANIFEST.SYNCED_AT, excluded(VEKTOR_MANIFEST.SYNCED_AT))
                .execute();
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

    @Override
    public List<VektorManifestRow> findByAppointmentWindow(LocalDateTime windowStart, LocalDateTime windowEnd) {
        return this.dsl
                .selectFrom(VEKTOR_MANIFEST)
                .where(VEKTOR_MANIFEST.PICKUP_APPOINTMENT_START.isNotNull())
                .and(VEKTOR_MANIFEST.ETA.isNotNull())
                .and(VEKTOR_MANIFEST.PICKUP_APPOINTMENT_START.lt(windowEnd))
                .and(VEKTOR_MANIFEST.ETA.gt(windowStart))
                .fetchInto(VektorManifest.class)
                .stream()
                .map(this::toRow)
                .toList();
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
                row.pickupAppointmentStart(),
                row.eta(),
                row.loadReference(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
