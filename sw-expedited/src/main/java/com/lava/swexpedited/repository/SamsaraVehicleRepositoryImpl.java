package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_VEHICLE;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraVehicle;
import com.lava.swexpedited.model.database.tables.records.SamsaraVehicleRecord;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep9;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraVehicleRepositoryImpl implements SamsaraVehicleRepository {

    private final DSLContext dsl;

    public SamsaraVehicleRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraVehicleRow> rows) {
        this.dsl.deleteFrom(SAMSARA_VEHICLE).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep9<
                        SamsaraVehicleRecord,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        String,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_VEHICLE,
                        SAMSARA_VEHICLE.ID,
                        SAMSARA_VEHICLE.VIN,
                        SAMSARA_VEHICLE.NAME,
                        SAMSARA_VEHICLE.MAKE,
                        SAMSARA_VEHICLE.MODEL,
                        SAMSARA_VEHICLE.YEAR,
                        SAMSARA_VEHICLE.LICENSE_PLATE,
                        SAMSARA_VEHICLE.RAW_RESPONSE,
                        SAMSARA_VEHICLE.SYNCED_AT);

        for (SamsaraVehicleRow row : rows) {
            insert.values(
                    row.id(),
                    row.vin(),
                    row.name(),
                    row.make(),
                    row.model(),
                    row.year(),
                    row.licensePlate(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraVehicleRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_VEHICLE).fetchInto(SamsaraVehicle.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraVehicleRow> findById(String id) {
        return this.dsl
                .selectFrom(SAMSARA_VEHICLE)
                .where(SAMSARA_VEHICLE.ID.eq(id))
                .fetchOptionalInto(SamsaraVehicle.class)
                .map(this::toRow);
    }

    private SamsaraVehicleRow toRow(SamsaraVehicle row) {
        return new SamsaraVehicleRow(
                row.id(),
                row.vin(),
                row.name(),
                row.make(),
                row.model(),
                row.year(),
                row.licensePlate(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
