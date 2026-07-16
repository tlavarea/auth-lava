package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraVehicleLocationRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraVehicleLocationRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - a live snapshot of "where's every
     * vehicle right now", refreshed on its own fast (~1 min) cadence, independent of the driver/assignment sync since
     * this table has no FK relationship to either.
     */
    void replaceAll(List<SamsaraVehicleLocationRow> rows);

    List<SamsaraVehicleLocationRow> findAll();

    Optional<SamsaraVehicleLocationRow> findByVehicleId(String vehicleId);
}
