package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.repository.VektorTimeOffRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorManifestMapper;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorTimeOffMapper;
import com.lava.swexpedited.vektor.VektorTimeOffRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * needs in bulk), maps and best-effort-matches each manifest against the Samsara driver roster, then upserts
 * vektor_manifest in one transaction. Unlike the other sync tasklets in this package (which fully replace their tables
 * every run), this deliberately never deletes rows - see {@link VektorManifestRepository#upsertAll}'s javadoc - so
 * completed manifests stick around as history for the Schedule view instead of disappearing once Vektor stops returning
 * them. A single tasklet, not chunked - same reasoning as {@code SamsaraDriverSyncJobConfig}: a handful of bulk HTTP
 * calls total per sync, not one per item, so there's no per-item retry/skip checkpointing to earn chunk-oriented
 * complexity.
 *
 * <p>Time-off entries are synced after manifests, in the same run: {@code TruckTimeOff/Get} groups entries by truck_id,
 * not driver_id (see {@code VektorTimeOffClient}'s javadoc), so attributing an entry to a driver leans on
 * {@code VektorManifestRepository#findLatestDriverIdByTruckId} - which is only accurate once this run's manifests (with
 * their own fresh truck_id/driver_id pairs) are already upserted.
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
    private final VektorDriverMatchStrategy vektorDriverMatchStrategy;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final VektorManifestRepository vektorManifestRepository;
    private final VektorTimeOffRepository vektorTimeOffRepository;
    private final VektorProperties vektorProperties;

    public VektorSyncTasklet(
            VektorAuthenticator vektorAuthenticator,
            VektorManifestClient vektorManifestClient,
            VektorDriverClient vektorDriverClient,
            VektorTimeOffClient vektorTimeOffClient,
            VektorManifestMapper vektorManifestMapper,
            VektorTimeOffMapper vektorTimeOffMapper,
            VektorDriverMatchStrategy vektorDriverMatchStrategy,
            SamsaraDriverRepository samsaraDriverRepository,
            VektorManifestRepository vektorManifestRepository,
            VektorTimeOffRepository vektorTimeOffRepository,
            VektorProperties vektorProperties) {
        this.vektorAuthenticator = vektorAuthenticator;
        this.vektorManifestClient = vektorManifestClient;
        this.vektorDriverClient = vektorDriverClient;
        this.vektorTimeOffClient = vektorTimeOffClient;
        this.vektorManifestMapper = vektorManifestMapper;
        this.vektorTimeOffMapper = vektorTimeOffMapper;
        this.vektorDriverMatchStrategy = vektorDriverMatchStrategy;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.vektorManifestRepository = vektorManifestRepository;
        this.vektorTimeOffRepository = vektorTimeOffRepository;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String jwt = this.vektorAuthenticator.authenticate();
        log.info("execute::vektor login successful");
        String companyId = this.vektorProperties.companyId();
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(this.vektorProperties.syncWindowDaysBack());
        List<VektorGrpcWeb.Message> manifests = this.vektorManifestClient.fetchManifests(
                jwt,
                companyId,
                this.vektorProperties.syncedStatuses(),
                windowStart,
                today.plusDays(this.vektorProperties.syncWindowDaysAhead()));
        Map<String, String> driverNamesById = this.vektorDriverClient.fetchDriverNamesById(jwt, companyId);
        List<SamsaraDriverRow> samsaraDrivers = this.samsaraDriverRepository.findAll();
        List<VektorManifestRow> manifestRows = manifests.stream()
                .map(manifest -> this.vektorManifestMapper.toRow(manifest, driverNamesById))
                .map(row -> matchSamsaraDriver(row.driverName(), samsaraDrivers)
                        .map(row::withMatchedSamsaraDriverId)
                        .orElse(row))
                .toList();

        this.vektorManifestRepository.upsertAll(manifestRows);
        log.info("execute::stored {} vektor manifests", manifestRows.size());

        List<VektorGrpcWeb.Message> timeOffEntries = this.vektorTimeOffClient.fetchTimeOff(jwt, companyId, windowStart);
        Map<String, String> latestDriverIdByTruckId = this.vektorManifestRepository.findLatestDriverIdByTruckId();
        List<VektorTimeOffRow> timeOffRows = timeOffEntries.stream()
                .map(this.vektorTimeOffMapper::toRow)
                .map(row -> matchTimeOffDriver(row, latestDriverIdByTruckId, driverNamesById, samsaraDrivers))
                .toList();

        this.vektorTimeOffRepository.upsertAll(timeOffRows);
        log.info("execute::stored {} vektor time-off entries", timeOffRows.size());
        return RepeatStatus.FINISHED;
    }

    private VektorTimeOffRow matchTimeOffDriver(
            VektorTimeOffRow row,
            Map<String, String> latestDriverIdByTruckId,
            Map<String, String> driverNamesById,
            List<SamsaraDriverRow> samsaraDrivers) {
        String driverId = latestDriverIdByTruckId.get(row.truckId());
        String driverName = driverId == null ? null : driverNamesById.get(driverId);
        return matchSamsaraDriver(driverName, samsaraDrivers)
                .map(row::withMatchedSamsaraDriverId)
                .orElse(row);
    }

    private Optional<String> matchSamsaraDriver(String driverName, List<SamsaraDriverRow> samsaraDrivers) {
        return this.vektorDriverMatchStrategy.match(driverName, samsaraDrivers);
    }
}
