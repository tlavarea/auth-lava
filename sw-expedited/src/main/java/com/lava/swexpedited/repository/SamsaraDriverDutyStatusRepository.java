package com.lava.swexpedited.repository;

import com.lava.swexpedited.samsara.SamsaraDriverDutyStatusRow;
import java.util.List;
import java.util.Optional;

public interface SamsaraDriverDutyStatusRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - a live snapshot of "what's every
     * driver's duty status right now", refreshed on its own fast (~1 min) cadence, independent of the driver roster
     * sync since this table has no FK relationship to it.
     */
    void replaceAll(List<SamsaraDriverDutyStatusRow> rows);

    List<SamsaraDriverDutyStatusRow> findAll();

    Optional<SamsaraDriverDutyStatusRow> findByDriverId(String driverId);
}
