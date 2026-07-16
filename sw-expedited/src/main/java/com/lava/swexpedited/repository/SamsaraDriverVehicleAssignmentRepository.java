package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraDriverVehicleAssignmentRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraDriverVehicleAssignmentRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - one row per driver, always the
     * current assignment, never a history. Must run after {@code SamsaraDriverRepository.replaceAll} in the same sync
     * tasklet so every row's driver_id FK resolves against the just-synced driver roster.
     */
    void replaceAll(List<SamsaraDriverVehicleAssignmentRow> rows);

    List<SamsaraDriverVehicleAssignmentRow> findAll();

    Optional<SamsaraDriverVehicleAssignmentRow> findByDriverId(String driverId);
}
