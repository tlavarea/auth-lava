package com.lava.swexpedited.repository;

import com.lava.swexpedited.vektor.VektorManifestRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VektorManifestRepository {

    /**
     * Upserts {@code rows} keyed on manifest_number - unlike {@code ShipmentListingRepository#replaceAll}/
     * {@code SamsaraDriverRepository#replaceAll}, this never deletes existing rows. Manifests/Get returns "what's
     * active right now" for the synced effective_status filter, so once a manifest completes and stops being returned,
     * its row simply stops being touched and stays in the table as a historical record - this is what lets the Schedule
     * view show weeks other than the current one. See 011's changelog comment.
     */
    void upsertAll(List<VektorManifestRow> rows);

    List<VektorManifestRow> findAll();

    Optional<VektorManifestRow> findByManifestNumber(long manifestNumber);

    /**
     * Manifests whose scheduled pickup->dropoff window (pickup_appointment_start, eta) overlaps [{@code windowStart},
     * {@code windowEnd}) - backs the Schedule view's week navigation. Manifests missing either timestamp are excluded,
     * matching the frontend's rule that both are required to render a schedule segment.
     */
    List<VektorManifestRow> findByAppointmentWindow(LocalDateTime windowStart, LocalDateTime windowEnd);

    /**
     * The most-recently-active driver for each truck, derived from vektor_manifest's retained history (not just the
     * current sync run - see {@code upsertAll}'s javadoc) rather than any direct truck-roster lookup, since Vektor has
     * no captured RPC that maps a truck to its currently-assigned driver directly. Best-effort, same spirit as
     * {@code matched_samsara_driver_id}: this is how a vektor_time_off entry (keyed by truck_id, not driver_id - see
     * {@code VektorManifestMapper}'s javadoc) gets attributed to a driver on the Schedule page.
     */
    Map<String, String> findLatestDriverIdByTruckId();
}
