package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorTimeOffRow;
import java.time.LocalDateTime;
import java.util.List;

public interface VektorTimeOffRepository {

    /**
     * Upserts {@code rows} keyed on id - same retained-history convention as {@code VektorManifestRepository
     * #upsertAll}, so a time-off block that's no longer returned by a later sync (e.g. it ended) stays in the table
     * rather than disappearing, letting the Schedule view show past weeks.
     */
    void upsertAll(List<VektorTimeOffRow> rows);

    /**
     * Time-off blocks whose [{@code startAt}, {@code endAt}] window overlaps [{@code windowStart}, {@code windowEnd}) -
     * backs the Schedule view's week navigation, same overlap semantics as {@code VektorManifestRepository
     * #findByAppointmentWindow}.
     */
    List<VektorTimeOffRow> findByWindow(LocalDateTime windowStart, LocalDateTime windowEnd);
}
