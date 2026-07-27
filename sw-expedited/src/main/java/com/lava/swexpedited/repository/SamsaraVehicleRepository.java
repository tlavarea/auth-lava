package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraVehicleRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - Samsara's vehicle roster is a live
     * snapshot, not a stream of changes to merge, same reasoning as {@code SamsaraDriverRepository.replaceAll}.
     */
    void replaceAll(List<SamsaraVehicleRow> rows);

    List<SamsaraVehicleRow> findAll();

    Optional<SamsaraVehicleRow> findById(String id);
}
