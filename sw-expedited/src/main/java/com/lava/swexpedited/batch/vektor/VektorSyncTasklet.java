package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorManifestMapper;
import com.lava.swexpedited.vektor.VektorManifestRow;
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
 * needs in bulk), maps and best-effort-matches each manifest against the Samsara driver roster, then replaces
 * vektor_manifest in one transaction. A single tasklet, not chunked - same reasoning as
 * {@code SamsaraDriverSyncJobConfig}: a handful of bulk HTTP calls total per sync, not one per item, so there's no
 * per-item retry/skip checkpointing to earn chunk-oriented complexity.
 */
@Component
@Slf4j
public class VektorSyncTasklet implements Tasklet {

    private final VektorAuthenticator vektorAuthenticator;
    private final VektorManifestClient vektorManifestClient;
    private final VektorDriverClient vektorDriverClient;
    private final VektorManifestMapper vektorManifestMapper;
    private final VektorDriverMatchStrategy vektorDriverMatchStrategy;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final VektorManifestRepository vektorManifestRepository;
    private final VektorProperties vektorProperties;

    public VektorSyncTasklet(
            VektorAuthenticator vektorAuthenticator,
            VektorManifestClient vektorManifestClient,
            VektorDriverClient vektorDriverClient,
            VektorManifestMapper vektorManifestMapper,
            VektorDriverMatchStrategy vektorDriverMatchStrategy,
            SamsaraDriverRepository samsaraDriverRepository,
            VektorManifestRepository vektorManifestRepository,
            VektorProperties vektorProperties) {
        this.vektorAuthenticator = vektorAuthenticator;
        this.vektorManifestClient = vektorManifestClient;
        this.vektorDriverClient = vektorDriverClient;
        this.vektorManifestMapper = vektorManifestMapper;
        this.vektorDriverMatchStrategy = vektorDriverMatchStrategy;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.vektorManifestRepository = vektorManifestRepository;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String jwt = this.vektorAuthenticator.authenticate();
        log.info("execute::vektor login successful");
        String companyId = this.vektorProperties.companyId();
        List<VektorGrpcWeb.Message> manifests =
                this.vektorManifestClient.fetchManifests(jwt, companyId, this.vektorProperties.syncedStatuses());
        Map<String, String> driverNamesById = this.vektorDriverClient.fetchDriverNamesById(jwt, companyId);
        List<SamsaraDriverRow> samsaraDrivers = this.samsaraDriverRepository.findAll();
        List<VektorManifestRow> rows = manifests.stream()
                .map(manifest -> this.vektorManifestMapper.toRow(manifest, driverNamesById))
                .map(row -> matchSamsaraDriver(row, samsaraDrivers))
                .toList();

        this.vektorManifestRepository.replaceAll(rows);
        log.info("execute::stored {} vektor manifests", rows.size());
        return RepeatStatus.FINISHED;
    }

    private VektorManifestRow matchSamsaraDriver(VektorManifestRow row, List<SamsaraDriverRow> samsaraDrivers) {
        return this.vektorDriverMatchStrategy
                .match(row, samsaraDrivers)
                .map(row::withMatchedSamsaraDriverId)
                .orElse(row);
    }
}
