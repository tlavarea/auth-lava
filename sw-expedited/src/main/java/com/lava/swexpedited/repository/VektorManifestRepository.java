package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorManifestRow;
import java.util.List;
import java.util.Optional;

public interface VektorManifestRepository {

    /**
     * Replaces the entire table contents with {@code rows} in a single transaction. Manifests/Get returns "what's
     * active right now" for the synced effective_status filter, not a change log, so a sync run has nothing to diff
     * against the previous one - it just becomes the new truth, same convention as
     * {@code ShipmentListingRepository#replaceAll}/{@code SamsaraDriverRepository#replaceAll}.
     */
    void replaceAll(List<VektorManifestRow> rows);

    List<VektorManifestRow> findAll();

    Optional<VektorManifestRow> findByManifestNumber(long manifestNumber);
}
