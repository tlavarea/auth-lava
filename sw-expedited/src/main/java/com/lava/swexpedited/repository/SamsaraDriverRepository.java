package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraDriverRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraDriverRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - Samsara's active-driver list is a
     * live snapshot, not a stream of changes to merge, so a sync run has nothing to diff against the previous one. The
     * delete cascades into samsara_driver_vehicle_assignment for free (see 004-create-samsara-driver-vehicle-assignment
     * .yaml's FK), which is why this must run before {@code SamsaraDriverVehicleAssignmentRepository.replaceAll} in the
     * same sync tasklet.
     */
    void replaceAll(List<SamsaraDriverRow> rows);

    List<SamsaraDriverRow> findAll();

    Optional<SamsaraDriverRow> findById(String id);
}
