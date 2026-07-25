package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.VEKTOR_TRUCK;

import com.lava.swexpedited.model.database.tables.pojos.VektorTruck;
import com.lava.swexpedited.model.database.tables.records.VektorTruckRecord;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep11;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VektorTruckRepositoryImpl implements VektorTruckRepository {

    private final DSLContext dsl;

    public VektorTruckRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<VektorTruckRow> rows) {
        this.dsl.deleteFrom(VEKTOR_TRUCK).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep11<
                        VektorTruckRecord,
                        String,
                        String,
                        Integer,
                        String,
                        String,
                        String,
                        Integer,
                        String,
                        String,
                        JSON,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        VEKTOR_TRUCK,
                        VEKTOR_TRUCK.ID,
                        VEKTOR_TRUCK.TRUCK_NUMBER,
                        VEKTOR_TRUCK.STATUS_CODE,
                        VEKTOR_TRUCK.VIN,
                        VEKTOR_TRUCK.MAKE,
                        VEKTOR_TRUCK.MODEL,
                        VEKTOR_TRUCK.YEAR,
                        VEKTOR_TRUCK.CURRENT_TRAILER_ID,
                        VEKTOR_TRUCK.CURRENT_DRIVER_ID,
                        VEKTOR_TRUCK.RAW_RESPONSE,
                        VEKTOR_TRUCK.SYNCED_AT);

        for (VektorTruckRow row : rows) {
            insert.values(
                    row.id(),
                    row.truckNumber(),
                    row.statusCode(),
                    row.vin(),
                    row.make(),
                    row.model(),
                    row.year(),
                    row.currentTrailerId(),
                    row.currentDriverId(),
                    JSON.valueOf(row.rawResponse()),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<VektorTruckRow> findAll() {
        return this.dsl.selectFrom(VEKTOR_TRUCK).fetchInto(VektorTruck.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<VektorTruckRow> findById(String id) {
        return this.dsl
                .selectFrom(VEKTOR_TRUCK)
                .where(VEKTOR_TRUCK.ID.eq(id))
                .fetchOptionalInto(VektorTruck.class)
                .map(this::toRow);
    }

    @Override
    public Map<String, String> findCurrentDriverIdByTruckId() {
        return this.dsl
                .select(VEKTOR_TRUCK.ID, VEKTOR_TRUCK.CURRENT_DRIVER_ID)
                .from(VEKTOR_TRUCK)
                .where(VEKTOR_TRUCK.CURRENT_DRIVER_ID.isNotNull())
                .fetchMap(VEKTOR_TRUCK.ID, VEKTOR_TRUCK.CURRENT_DRIVER_ID);
    }

    private VektorTruckRow toRow(VektorTruck row) {
        return new VektorTruckRow(
                row.id(),
                row.truckNumber(),
                row.statusCode(),
                row.vin(),
                row.make(),
                row.model(),
                row.year(),
                row.currentTrailerId(),
                row.currentDriverId(),
                row.rawResponse().data(),
                row.syncedAt());
    }
}
