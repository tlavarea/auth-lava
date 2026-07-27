package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_VEHICLE_DIAGNOSTICS;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraVehicleDiagnostics;
import com.lava.swexpedited.model.database.tables.records.SamsaraVehicleDiagnosticsRecord;
import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep12;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraVehicleDiagnosticsRepositoryImpl implements SamsaraVehicleDiagnosticsRepository {

    private final DSLContext dsl;

    public SamsaraVehicleDiagnosticsRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraVehicleDiagnosticsRow> rows) {
        this.dsl.deleteFrom(SAMSARA_VEHICLE_DIAGNOSTICS).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep12<
                        SamsaraVehicleDiagnosticsRecord,
                        String,
                        Integer,
                        Long,
                        Long,
                        JSON,
                        String,
                        Integer,
                        Integer,
                        Integer,
                        Integer,
                        Integer,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_VEHICLE_DIAGNOSTICS,
                        SAMSARA_VEHICLE_DIAGNOSTICS.VEHICLE_ID,
                        SAMSARA_VEHICLE_DIAGNOSTICS.FUEL_PERCENT,
                        SAMSARA_VEHICLE_DIAGNOSTICS.ODOMETER_METERS,
                        SAMSARA_VEHICLE_DIAGNOSTICS.ENGINE_SECONDS,
                        SAMSARA_VEHICLE_DIAGNOSTICS.FAULT_CODES,
                        SAMSARA_VEHICLE_DIAGNOSTICS.ENGINE_STATE,
                        SAMSARA_VEHICLE_DIAGNOSTICS.DEF_LEVEL_MILLI_PERCENT,
                        SAMSARA_VEHICLE_DIAGNOSTICS.BATTERY_MILLI_VOLTS,
                        SAMSARA_VEHICLE_DIAGNOSTICS.COOLANT_TEMP_MILLI_C,
                        SAMSARA_VEHICLE_DIAGNOSTICS.ENGINE_RPM,
                        SAMSARA_VEHICLE_DIAGNOSTICS.ENGINE_LOAD_PERCENT,
                        SAMSARA_VEHICLE_DIAGNOSTICS.SYNCED_AT);

        for (SamsaraVehicleDiagnosticsRow row : rows) {
            insert.values(
                    row.vehicleId(),
                    row.fuelPercent(),
                    row.odometerMeters(),
                    row.engineSeconds(),
                    row.faultCodes() != null ? JSON.valueOf(row.faultCodes()) : null,
                    row.engineState(),
                    row.defLevelMilliPercent(),
                    row.batteryMilliVolts(),
                    row.coolantTempMilliC(),
                    row.engineRpm(),
                    row.engineLoadPercent(),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraVehicleDiagnosticsRow> findAll() {
        return this.dsl.selectFrom(SAMSARA_VEHICLE_DIAGNOSTICS).fetchInto(SamsaraVehicleDiagnostics.class).stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraVehicleDiagnosticsRow> findByVehicleId(String vehicleId) {
        return this.dsl
                .selectFrom(SAMSARA_VEHICLE_DIAGNOSTICS)
                .where(SAMSARA_VEHICLE_DIAGNOSTICS.VEHICLE_ID.eq(vehicleId))
                .fetchOptionalInto(SamsaraVehicleDiagnostics.class)
                .map(this::toRow);
    }

    private SamsaraVehicleDiagnosticsRow toRow(SamsaraVehicleDiagnostics row) {
        return new SamsaraVehicleDiagnosticsRow(
                row.vehicleId(),
                row.fuelPercent(),
                row.odometerMeters(),
                row.engineSeconds(),
                row.faultCodes() != null ? row.faultCodes().data() : null,
                row.engineState(),
                row.defLevelMilliPercent(),
                row.batteryMilliVolts(),
                row.coolantTempMilliC(),
                row.engineRpm(),
                row.engineLoadPercent(),
                row.syncedAt());
    }
}
