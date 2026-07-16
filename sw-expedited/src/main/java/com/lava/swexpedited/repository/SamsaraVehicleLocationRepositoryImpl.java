package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_VEHICLE_LOCATION;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraVehicleLocation;
import com.lava.swexpedited.model.database.tables.records.SamsaraVehicleLocationRecord;
import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep9;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraVehicleLocationRepositoryImpl implements SamsaraVehicleLocationRepository {

    private final DSLContext dsl;

    public SamsaraVehicleLocationRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraVehicleLocationRow> rows) {
        this.dsl.deleteFrom(SAMSARA_VEHICLE_LOCATION).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep9<
                        SamsaraVehicleLocationRecord,
                        String,
                        String,
                        BigDecimal,
                        BigDecimal,
                        BigDecimal,
                        BigDecimal,
                        LocalDateTime,
                        String,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_VEHICLE_LOCATION,
                        SAMSARA_VEHICLE_LOCATION.VEHICLE_ID,
                        SAMSARA_VEHICLE_LOCATION.VEHICLE_NAME,
                        SAMSARA_VEHICLE_LOCATION.LATITUDE,
                        SAMSARA_VEHICLE_LOCATION.LONGITUDE,
                        SAMSARA_VEHICLE_LOCATION.HEADING,
                        SAMSARA_VEHICLE_LOCATION.SPEED,
                        SAMSARA_VEHICLE_LOCATION.LOCATION_TIME,
                        SAMSARA_VEHICLE_LOCATION.FORMATTED_LOCATION,
                        SAMSARA_VEHICLE_LOCATION.SYNCED_AT);

        for (SamsaraVehicleLocationRow row : rows) {
            insert.values(
                    row.vehicleId(),
                    row.vehicleName(),
                    row.latitude(),
                    row.longitude(),
                    row.heading(),
                    row.speed(),
                    row.locationTime(),
                    row.formattedLocation(),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraVehicleLocationRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_VEHICLE_LOCATION).fetchInto(SamsaraVehicleLocation.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraVehicleLocationRow> findByVehicleId(String vehicleId) {
        return this.dsl
                .selectFrom(SAMSARA_VEHICLE_LOCATION)
                .where(SAMSARA_VEHICLE_LOCATION.VEHICLE_ID.eq(vehicleId))
                .fetchOptionalInto(SamsaraVehicleLocation.class)
                .map(this::toRow);
    }

    private SamsaraVehicleLocationRow toRow(SamsaraVehicleLocation row) {
        return new SamsaraVehicleLocationRow(
                row.vehicleId(),
                row.vehicleName(),
                row.latitude(),
                row.longitude(),
                row.heading(),
                row.speed(),
                row.locationTime(),
                row.formattedLocation(),
                row.syncedAt());
    }
}
