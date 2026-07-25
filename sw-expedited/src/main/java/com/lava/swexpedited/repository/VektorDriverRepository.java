package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorDriverRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VektorDriverRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - Vektor's driver roster is a live
     * snapshot, not a stream of changes to merge, so a sync run has nothing to diff against the previous one - same
     * convention as {@link SamsaraDriverRepository#replaceAll}.
     */
    void replaceAll(List<VektorDriverRow> rows);

    List<VektorDriverRow> findAll();

    Optional<VektorDriverRow> findById(String id);

    /**
     * vektor_driver.id -&gt; matched_samsara_driver_id, non-null entries only - this is how {@code VektorSyncTasklet}
     * resolves a manifest's or time-off entry's driver to a Samsara driver id, replacing the old approach of re-running
     * {@code NameNormalizingDriverMatchStrategy} live against every individual row.
     */
    Map<String, String> findMatchedSamsaraDriverIdById();
}
