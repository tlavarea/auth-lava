package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraTrailerRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - Samsara's trailer roster is a live
     * snapshot, not a stream of changes to merge, same reasoning as {@code SamsaraVehicleRepository.replaceAll}.
     */
    void replaceAll(List<SamsaraTrailerRow> rows);

    List<SamsaraTrailerRow> findAll();

    Optional<SamsaraTrailerRow> findById(String id);
}
