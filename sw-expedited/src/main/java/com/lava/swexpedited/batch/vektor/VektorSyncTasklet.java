package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.repository.VektorTimeOffRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorManifestMapper;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorTimeOffMapper;
import com.lava.swexpedited.vektor.VektorTimeOffRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Logs into Vektor, fetches the currently-synced-status manifests and the full driver roster (one call each, not
 * per-manifest - unlike GFM's per-shipment {@code getBid} calls, Vektor's endpoints already return everything this app
 * needs in bulk), maps each manifest and resolves its Samsara driver via
 * {@code vektor_driver.matched_samsara_driver_id} (see {@code VektorFleetSyncTasklet} - that's where the actual name
 * matching against the Samsara roster happens now, once per Vektor driver rather than live per manifest/time-off row),
 * then upserts vektor_manifest in one transaction. Unlike the other sync tasklets in this package (which fully replace
 * their tables every run), this doesn't blanket-delete rows - see {@link VektorManifestRepository#upsertAll}'s javadoc
 * - so completed manifests stick around as history for the Schedule view instead of disappearing once Vektor stops
 * returning them. It does prune the narrower case of a non-terminal manifest Vektor stops returning (reassigned/
 * canceled rather than completed) via {@link VektorManifestRepository#pruneSupersededManifests} right after the upsert,
 * so a driver's Schedule segments can't end up doubled up between a superseded manifest and whatever replaced it - see
 * that method's javadoc. A single tasklet, not chunked - same reasoning as {@code SamsaraDriverSyncJobConfig}: a
 * handful of bulk HTTP calls total per sync, not one per item, so there's no per-item retry/skip checkpointing to earn
 * chunk-oriented complexity.
 *
 * <p>Time-off entries are synced after manifests, in the same run: {@code TruckTimeOff/Get} groups entries by truck_id,
 * not driver_id (see {@code VektorTimeOffClient}'s javadoc), so attributing an entry to a driver leans on
 * {@code VektorTruckRepository#findCurrentDriverIdByTruckId} (Vektor's own current truck-&gt;driver assignment, synced
 * independently by {@code VektorFleetSyncTasklet}) followed by the same {@code vektor_driver} lookup manifests use.
 */
@Component
@Slf4j
public class VektorSyncTasklet implements Tasklet {

    private final VektorAuthenticator vektorAuthenticator;
    private final VektorManifestClient vektorManifestClient;
    private final VektorDriverClient vektorDriverClient;
    private final VektorTimeOffClient vektorTimeOffClient;
    private final VektorManifestMapper vektorManifestMapper;
    private final VektorTimeOffMapper vektorTimeOffMapper;
    private final VektorManifestRepository vektorManifestRepository;
    private final VektorTimeOffRepository vektorTimeOffRepository;
    private final VektorTruckRepository vektorTruckRepository;
    private final VektorDriverRepository vektorDriverRepository;
    private final VektorProperties vektorProperties;

    public VektorSyncTasklet(
            VektorAuthenticator vektorAuthenticator,
            VektorManifestClient vektorManifestClient,
            VektorDriverClient vektorDriverClient,
            VektorTimeOffClient vektorTimeOffClient,
            VektorManifestMapper vektorManifestMapper,
            VektorTimeOffMapper vektorTimeOffMapper,
            VektorManifestRepository vektorManifestRepository,
            VektorTimeOffRepository vektorTimeOffRepository,
            VektorTruckRepository vektorTruckRepository,
            VektorDriverRepository vektorDriverRepository,
            VektorProperties vektorProperties) {
        this.vektorAuthenticator = vektorAuthenticator;
        this.vektorManifestClient = vektorManifestClient;
        this.vektorDriverClient = vektorDriverClient;
        this.vektorTimeOffClient = vektorTimeOffClient;
        this.vektorManifestMapper = vektorManifestMapper;
        this.vektorTimeOffMapper = vektorTimeOffMapper;
        this.vektorManifestRepository = vektorManifestRepository;
        this.vektorTimeOffRepository = vektorTimeOffRepository;
        this.vektorTruckRepository = vektorTruckRepository;
        this.vektorDriverRepository = vektorDriverRepository;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String jwt = this.vektorAuthenticator.authenticate();
        log.info("execute::vektor login successful");
        String companyId = this.vektorProperties.companyId();
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(this.vektorProperties.syncWindowDaysBack());
        Map<String, String> matchedSamsaraDriverIdByVektorDriverId =
                this.vektorDriverRepository.findMatchedSamsaraDriverIdById();

        List<VektorGrpcWeb.Message> manifests = this.vektorManifestClient.fetchManifests(
                jwt,
                companyId,
                this.vektorProperties.syncedStatuses(),
                windowStart,
                today.plusDays(this.vektorProperties.syncWindowDaysAhead()));
        Map<String, String> driverNamesById = this.vektorDriverClient.fetchDriverNamesById(jwt, companyId);
        List<VektorManifestRow> manifestRows = manifests.stream()
                .map(manifest -> this.vektorManifestMapper.toRow(manifest, driverNamesById))
                .map(row -> withMatchedSamsaraDriver(row, matchedSamsaraDriverIdByVektorDriverId.get(row.driverId())))
                .toList();

        this.vektorManifestRepository.upsertAll(manifestRows);
        this.vektorManifestRepository.pruneSupersededManifests(
                manifestRows.stream().map(VektorManifestRow::manifestNumber).toList());
        log.info("execute::stored {} vektor manifests", manifestRows.size());

        List<VektorGrpcWeb.Message> timeOffEntries = this.vektorTimeOffClient.fetchTimeOff(jwt, companyId, windowStart);
        Map<String, String> currentDriverIdByTruckId = this.vektorTruckRepository.findCurrentDriverIdByTruckId();
        List<VektorTimeOffRow> timeOffRows = timeOffEntries.stream()
                .map(this.vektorTimeOffMapper::toRow)
                .map(row -> matchTimeOffDriver(row, currentDriverIdByTruckId, matchedSamsaraDriverIdByVektorDriverId))
                .toList();

        this.vektorTimeOffRepository.upsertAll(timeOffRows);
        log.info("execute::stored {} vektor time-off entries", timeOffRows.size());
        return RepeatStatus.FINISHED;
    }

    private VektorTimeOffRow matchTimeOffDriver(
            VektorTimeOffRow row,
            Map<String, String> currentDriverIdByTruckId,
            Map<String, String> matchedSamsaraDriverIdByVektorDriverId) {
        String vektorDriverId = currentDriverIdByTruckId.get(row.truckId());
        String matchedSamsaraDriverId =
                vektorDriverId == null ? null : matchedSamsaraDriverIdByVektorDriverId.get(vektorDriverId);
        return withMatchedSamsaraDriver(row, matchedSamsaraDriverId);
    }

    private VektorManifestRow withMatchedSamsaraDriver(VektorManifestRow row, String matchedSamsaraDriverId) {
        return matchedSamsaraDriverId == null ? row : row.withMatchedSamsaraDriverId(matchedSamsaraDriverId);
    }

    private VektorTimeOffRow withMatchedSamsaraDriver(VektorTimeOffRow row, String matchedSamsaraDriverId) {
        return matchedSamsaraDriverId == null ? row : row.withMatchedSamsaraDriverId(matchedSamsaraDriverId);
    }
}
