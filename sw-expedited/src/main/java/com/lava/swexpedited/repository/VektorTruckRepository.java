package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VektorTruckRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction - same convention as
     * {@link SamsaraDriverRepository#replaceAll}.
     */
    void replaceAll(List<VektorTruckRow> rows);

    List<VektorTruckRow> findAll();

    Optional<VektorTruckRow> findById(String id);

    /**
     * truck_id -&gt; current_driver_id (a vektor_driver.id, not yet resolved to a Samsara driver id), non-null entries
     * only - a direct truck-&gt;driver lookup from Vektor's own {@code Trucks/Get} roster, replacing the old heuristic
     * of inferring "who's driving this truck" from vektor_manifest's retained pickup-appointment history.
     */
    Map<String, String> findCurrentDriverIdByTruckId();
}
