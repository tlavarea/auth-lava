package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_DRIVER;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraDriver;
import com.lava.swexpedited.model.database.tables.records.SamsaraDriverRecord;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep13;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraDriverRepositoryImpl implements SamsaraDriverRepository {

    private final DSLContext dsl;

    public SamsaraDriverRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraDriverRow> rows) {
        this.dsl.deleteFrom(SAMSARA_DRIVER).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep13<
                        SamsaraDriverRecord,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        LocalDateTime,
                        LocalDateTime,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_DRIVER,
                        SAMSARA_DRIVER.ID,
                        SAMSARA_DRIVER.NAME,
                        SAMSARA_DRIVER.USERNAME,
                        SAMSARA_DRIVER.EMAIL,
                        SAMSARA_DRIVER.PHONE,
                        SAMSARA_DRIVER.LICENSE_NUMBER,
                        SAMSARA_DRIVER.LICENSE_STATE,
                        SAMSARA_DRIVER.ACTIVATION_STATUS,
                        SAMSARA_DRIVER.TAGS,
                        SAMSARA_DRIVER.CREATED_AT_TIME,
                        SAMSARA_DRIVER.UPDATED_AT_TIME,
                        SAMSARA_DRIVER.RAW_RESPONSE,
                        SAMSARA_DRIVER.SYNCED_AT);

        for (SamsaraDriverRow row : rows) {
            insert.values(
                    row.id(),
                    row.name(),
                    row.username(),
                    row.email(),
                    row.phone(),
                    row.licenseNumber(),
                    row.licenseState(),
                    row.activationStatus(),
                    row.tags(),
                    row.createdAtTime(),
                    row.updatedAtTime(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraDriverRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_DRIVER).fetchInto(SamsaraDriver.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraDriverRow> findById(String id) {
        return this.dsl
                .selectFrom(SAMSARA_DRIVER)
                .where(SAMSARA_DRIVER.ID.eq(id))
                .fetchOptionalInto(SamsaraDriver.class)
                .map(this::toRow);
    }

    private SamsaraDriverRow toRow(SamsaraDriver row) {
        return new SamsaraDriverRow(
                row.id(),
                row.name(),
                row.username(),
                row.email(),
                row.phone(),
                row.licenseNumber(),
                row.licenseState(),
                row.activationStatus(),
                row.tags(),
                row.createdAtTime(),
                row.updatedAtTime(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
