package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraVehicleDiagnosticsRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraVehicleDiagnosticsRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - a live snapshot of every vehicle's
     * diagnostics, refreshed on its own cadence, independent of the vehicle roster sync since this table has no FK
     * relationship to it (same reasoning as {@code SamsaraVehicleLocationRepository.replaceAll}).
     */
    void replaceAll(List<SamsaraVehicleDiagnosticsRow> rows);

    List<SamsaraVehicleDiagnosticsRow> findAll();

    Optional<SamsaraVehicleDiagnosticsRow> findByVehicleId(String vehicleId);
}
