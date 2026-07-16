package com.lava.swexpedited.repository;

import static com.lava.swexpedited.model.database.Tables.SAMSARA_DRIVER_VEHICLE_ASSIGNMENT;

import com.lava.swexpedited.model.database.tables.pojos.SamsaraDriverVehicleAssignment;
import com.lava.swexpedited.model.database.tables.records.SamsaraDriverVehicleAssignmentRecord;
import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep6;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SamsaraDriverVehicleAssignmentRepositoryImpl implements SamsaraDriverVehicleAssignmentRepository {

    private final DSLContext dsl;

    public SamsaraDriverVehicleAssignmentRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void replaceAll(List<SamsaraDriverVehicleAssignmentRow> rows) {
        this.dsl.deleteFrom(SAMSARA_DRIVER_VEHICLE_ASSIGNMENT).execute();

        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        InsertValuesStep6<
                        SamsaraDriverVehicleAssignmentRecord,
                        String,
                        String,
                        String,
                        LocalDateTime,
                        LocalDateTime,
                        LocalDateTime>
                insert = this.dsl.insertInto(
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.DRIVER_ID,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.VEHICLE_ID,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.VEHICLE_NAME,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.START_TIME,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.ASSIGNED_AT_TIME,
                        SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.SYNCED_AT);

        for (SamsaraDriverVehicleAssignmentRow row : rows) {
            insert.values(
                    row.driverId(),
                    row.vehicleId(),
                    row.vehicleName(),
                    row.startTime(),
                    row.assignedAtTime(),
                    syncedAt);
        }

        insert.execute();
    }

    @Override
    public List<SamsaraDriverVehicleAssignmentRow> findAll() {
        return this.dsl
                .selectFrom(SAMSARA_DRIVER_VEHICLE_ASSIGNMENT)
                .fetchInto(SamsaraDriverVehicleAssignment.class)
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Override
    public Optional<SamsaraDriverVehicleAssignmentRow> findByDriverId(String driverId) {
        return this.dsl
                .selectFrom(SAMSARA_DRIVER_VEHICLE_ASSIGNMENT)
                .where(SAMSARA_DRIVER_VEHICLE_ASSIGNMENT.DRIVER_ID.eq(driverId))
                .fetchOptionalInto(SamsaraDriverVehicleAssignment.class)
                .map(this::toRow);
    }

    private SamsaraDriverVehicleAssignmentRow toRow(SamsaraDriverVehicleAssignment row) {
        return new SamsaraDriverVehicleAssignmentRow(
                row.driverId(),
                row.vehicleId(),
                row.vehicleName(),
                row.startTime(),
                row.assignedAtTime(),
                row.syncedAt());
    }
}
