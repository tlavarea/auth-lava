package com.lava.swexpedited.service;

import com.lava.swexpedited.batch.vektor.VektorSessionProvider;
import com.lava.swexpedited.batch.vektor.VektorTruckEtaStatesClient;
import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.manifest.ManifestEtaResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorEtaSnapshotRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorManifestStop;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves a manifest's live ETA to its current active stop via Vektor's own {@code Manifests/TruckEtaStatesGet} - the
 * "ETA / Left X mi" block a dispatcher sees in Vektor's own UI, which the Schedule page's manifest detail pane
 * previously had no equivalent for. {@code TruckEtaStatesGet} returns a manifest's <em>entire</em> ETA history
 * (thousands of snapshots for a multi-day manifest, each targeting one stop) rather than just "the current one", so
 * this filters to snapshots targeting the current active stop (the first not yet checked out of, same "next incomplete
 * stop" concept {@link ManifestRouteServiceImpl} already uses for its route-splice) and takes the last one in response
 * order - snapshots are recorded append-only roughly once a minute in real captured traffic, so the last matching entry
 * is the most recently recorded one, more reliable than sorting by the ETA value itself (which naturally drifts as the
 * truck moves rather than only increasing). Live, on-demand, no persisted cache, same convention as
 * {@link ManifestRouteServiceImpl}/{@link ManifestDriverLocationServiceImpl}.
 *
 * <p>Cross-validated exactly against a real manifest (#1000588, Michael Goodson, Seguin TX as the active stop): the
 * last snapshot targeting that stop reported 552.86 remaining miles, 567 remaining minutes, and an estimated arrival of
 * 2026-07-19 02:16 - matching a real dispatch sheet's "Left 553 mi / 9h" and "ETA Jul 19, 2:16" exactly.
 */
@Service
public class ManifestEtaServiceImpl implements ManifestEtaService {

    private final VektorManifestRepository vektorManifestRepository;
    private final VektorSessionProvider vektorSessionProvider;
    private final VektorTruckEtaStatesClient vektorTruckEtaStatesClient;
    private final VektorProperties vektorProperties;

    public ManifestEtaServiceImpl(
            VektorManifestRepository vektorManifestRepository,
            VektorSessionProvider vektorSessionProvider,
            VektorTruckEtaStatesClient vektorTruckEtaStatesClient,
            VektorProperties vektorProperties) {
        this.vektorManifestRepository = vektorManifestRepository;
        this.vektorSessionProvider = vektorSessionProvider;
        this.vektorTruckEtaStatesClient = vektorTruckEtaStatesClient;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public Optional<ManifestEtaResponse> findEta(long manifestNumber) {
        return this.vektorManifestRepository
                .findByManifestNumber(manifestNumber)
                .flatMap(this::findEta);
    }

    private Optional<ManifestEtaResponse> findEta(VektorManifestRow manifest) {
        VektorManifestStop activeStop = firstIncompleteStop(manifest);
        if (activeStop == null || activeStop.stopId() == null) {
            return Optional.empty();
        }

        List<VektorEtaSnapshotRow> snapshots = this.vektorSessionProvider.withSession(jwt ->
                this.vektorTruckEtaStatesClient.fetch(jwt, this.vektorProperties.companyId(), manifest.manifestId()));

        VektorEtaSnapshotRow lastMatching = null;
        for (VektorEtaSnapshotRow snapshot : snapshots) {
            if (activeStop.stopId().equals(snapshot.targetStopId())) {
                lastMatching = snapshot;
            }
        }
        return Optional.ofNullable(lastMatching).map(ManifestEtaServiceImpl::toResponse);
    }

    private static VektorManifestStop firstIncompleteStop(VektorManifestRow manifest) {
        for (VektorManifestStop stop : manifest.stops()) {
            if (stop.checkedOutAt() == null) {
                return stop;
            }
        }
        return null;
    }

    private static ManifestEtaResponse toResponse(VektorEtaSnapshotRow snapshot) {
        return new ManifestEtaResponse(
                snapshot.targetSequenceNumber(),
                snapshot.remainingMiles(),
                snapshot.remainingMinutes(),
                snapshot.estimatedArrival());
    }
}
