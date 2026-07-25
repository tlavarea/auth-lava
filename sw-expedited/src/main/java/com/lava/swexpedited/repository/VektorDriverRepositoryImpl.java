package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_DRIVER;

import com.lava.swexpedited.model.database.tables.pojos.VektorDriver;
import com.lava.swexpedited.model.database.tables.records.VektorDriverRecord;
import com.lava.swexpedited.vektor.VektorDriverRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VektorDriverRepositoryImpl implements VektorDriverRepository {

    private final DSLContext dsl;

    public VektorDriverRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<VektorDriverRow> rows) {
        this.dsl.deleteFrom(VEKTOR_DRIVER).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep8<VektorDriverRecord, String, String, String, String, String, String, JSON, LocalDateTime>
                insert = this.dsl.insertInto(
                        VEKTOR_DRIVER,
                        VEKTOR_DRIVER.ID,
                        VEKTOR_DRIVER.DRIVER_NUMBER,
                        VEKTOR_DRIVER.FULL_NAME,
                        VEKTOR_DRIVER.EMAIL,
                        VEKTOR_DRIVER.PHONE,
                        VEKTOR_DRIVER.MATCHED_SAMSARA_DRIVER_ID,
                        VEKTOR_DRIVER.RAW_RESPONSE,
                        VEKTOR_DRIVER.SYNCED_AT);

        for (VektorDriverRow row : rows) {
            insert.values(
                    row.id(),
                    row.driverNumber(),
                    row.fullName(),
                    row.email(),
                    row.phone(),
                    row.matchedSamsaraDriverId(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<VektorDriverRow> findAll() {
        return this.dsl.selectFrom(VEKTOR_DRIVER).fetchInto(VektorDriver.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<VektorDriverRow> findById(String id) {
        return this.dsl
                .selectFrom(VEKTOR_DRIVER)
                .where(VEKTOR_DRIVER.ID.eq(id))
                .fetchOptionalInto(VektorDriver.class)
                .map(this::toRow);
    }

    @Override
    public Map<String, String> findMatchedSamsaraDriverIdById() {
        return this.dsl
                .select(VEKTOR_DRIVER.ID, VEKTOR_DRIVER.MATCHED_SAMSARA_DRIVER_ID)
                .from(VEKTOR_DRIVER)
                .where(VEKTOR_DRIVER.MATCHED_SAMSARA_DRIVER_ID.isNotNull())
                .fetchMap(VEKTOR_DRIVER.ID, VEKTOR_DRIVER.MATCHED_SAMSARA_DRIVER_ID);
    }

    private VektorDriverRow toRow(VektorDriver row) {
        return new VektorDriverRow(
                row.id(),
                row.driverNumber(),
                row.fullName(),
                row.email(),
                row.phone(),
                row.matchedSamsaraDriverId(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
