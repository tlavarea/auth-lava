package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorManifestRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VektorManifestRepository {

    /**
     * Upserts {@code rows} keyed on manifest_number - unlike {@code ShipmentListingRepository#replaceAll}/
     * {@code SamsaraDriverRepository#replaceAll}, this never deletes rows on its own. Manifests/Get returns "what's
     * active right now" for the synced effective_status filter, so once a manifest completes and stops being returned,
     * its row simply stops being touched and stays in the table as a historical record - this is what lets the Schedule
     * view show weeks other than the current one. See 011's changelog comment. Pairs with
     * {@link #pruneSupersededManifests}, which does delete rows, but only ones that were never going to become history
     * in the first place (non-terminal manifests Vektor has dropped rather than completed).
     */
    void upsertAll(List<VektorManifestRow> rows);

    /**
     * Deletes manifests whose status isn't a terminal one (delivered/tonu) and whose manifest_number isn't in
     * {@code currentManifestNumbers} - i.e. manifests Vektor no longer returns as active, meaning they were
     * reassigned/canceled rather than completed (Vektor's own default syncedStatuses excludes
     * manifest_canceled/manifest_deleted, see VektorProperties' javadoc, so a canceled manifest simply stops appearing
     * in Manifests/Get rather than showing up with that status). Left unpruned, such a manifest keeps its last-known
     * pickup/dropoff window forever and shows up alongside whatever manifest actually replaced it on the Schedule view.
     * A no-op when {@code currentManifestNumbers} is empty, so an anomalous empty fetch (e.g. every manifest in a run
     * failed to map) can't wipe out every non-terminal manifest in the table.
     */
    void pruneSupersededManifests(List<Long> currentManifestNumbers);

    List<VektorManifestRow> findAll();

    Optional<VektorManifestRow> findByManifestNumber(long manifestNumber);

    /**
     * Manifests whose scheduled pickup->dropoff window (pickup_appointment_start, eta) overlaps [{@code windowStart},
     * {@code windowEnd}) - backs the Schedule view's week navigation. Manifests missing either timestamp are excluded,
     * matching the frontend's rule that both are required to render a schedule segment.
     */
    List<VektorManifestRow> findByAppointmentWindow(LocalDateTime windowStart, LocalDateTime windowEnd);
}
