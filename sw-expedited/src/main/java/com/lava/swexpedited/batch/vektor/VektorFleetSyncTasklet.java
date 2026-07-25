package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorDriverMapper;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerMapper;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckMapper;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Logs into Vektor once, then fetches and replaces the full driver/truck/trailer rosters in that order - a separate job
 * from {@link VektorSyncTasklet} (manifests/time-off) rather than a step chained onto it, since
 * {@code VektorAuthenticator#authenticate()} is already called fresh per job today with no shared-session plumbing
 * across jobs, and there's no ordering requirement between the two: {@code VektorSyncTasklet}'s driver-matching just
 * reads whatever this job last synced, tolerating staleness the same way independently-synced tables are already joined
 * elsewhere in this app (see {@code DriverTimelineServiceImpl}). Drivers are matched against the Samsara roster here,
 * once per driver, via {@link VektorDriverMatchStrategy} - this replaces the old approach of {@code VektorSyncTasklet}
 * re-running that same match live against every individual manifest/time-off row. A single tasklet, not chunked - same
 * reasoning as {@code VektorSyncTasklet}: a handful of bulk HTTP calls total per sync, not one per item.
 */
@Component
@Slf4j
public class VektorFleetSyncTasklet implements Tasklet {

    private final VektorAuthenticator vektorAuthenticator;
    private final VektorDriverClient vektorDriverClient;
    private final VektorTruckClient vektorTruckClient;
    private final VektorTrailerClient vektorTrailerClient;
    private final VektorDriverMapper vektorDriverMapper;
    private final VektorTruckMapper vektorTruckMapper;
    private final VektorTrailerMapper vektorTrailerMapper;
    private final VektorDriverMatchStrategy vektorDriverMatchStrategy;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final VektorDriverRepository vektorDriverRepository;
    private final VektorTruckRepository vektorTruckRepository;
    private final VektorTrailerRepository vektorTrailerRepository;
    private final VektorProperties vektorProperties;

    public VektorFleetSyncTasklet(
            VektorAuthenticator vektorAuthenticator,
            VektorDriverClient vektorDriverClient,
            VektorTruckClient vektorTruckClient,
            VektorTrailerClient vektorTrailerClient,
            VektorDriverMapper vektorDriverMapper,
            VektorTruckMapper vektorTruckMapper,
            VektorTrailerMapper vektorTrailerMapper,
            VektorDriverMatchStrategy vektorDriverMatchStrategy,
            SamsaraDriverRepository samsaraDriverRepository,
            VektorDriverRepository vektorDriverRepository,
            VektorTruckRepository vektorTruckRepository,
            VektorTrailerRepository vektorTrailerRepository,
            VektorProperties vektorProperties) {
        this.vektorAuthenticator = vektorAuthenticator;
        this.vektorDriverClient = vektorDriverClient;
        this.vektorTruckClient = vektorTruckClient;
        this.vektorTrailerClient = vektorTrailerClient;
        this.vektorDriverMapper = vektorDriverMapper;
        this.vektorTruckMapper = vektorTruckMapper;
        this.vektorTrailerMapper = vektorTrailerMapper;
        this.vektorDriverMatchStrategy = vektorDriverMatchStrategy;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.vektorDriverRepository = vektorDriverRepository;
        this.vektorTruckRepository = vektorTruckRepository;
        this.vektorTrailerRepository = vektorTrailerRepository;
        this.vektorProperties = vektorProperties;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String jwt = this.vektorAuthenticator.authenticate();
        log.info("execute::vektor login successful");
        String companyId = this.vektorProperties.companyId();

        List<SamsaraDriverRow> samsaraDrivers = this.samsaraDriverRepository.findAll();
        List<VektorDriverRow> driverRows = this.vektorDriverClient.fetchDrivers(jwt, companyId).stream()
                .map(this.vektorDriverMapper::toRow)
                .map(row -> this.vektorDriverMatchStrategy
                        .match(row.fullName(), samsaraDrivers)
                        .map(row::withMatchedSamsaraDriverId)
                        .orElse(row))
                .toList();
        this.vektorDriverRepository.replaceAll(driverRows);
        log.info("execute::stored {} vektor drivers", driverRows.size());

        List<VektorTruckRow> truckRows = this.vektorTruckClient.fetchTrucks(jwt, companyId).stream()
                .map(this.vektorTruckMapper::toRow)
                .toList();
        this.vektorTruckRepository.replaceAll(truckRows);
        log.info("execute::stored {} vektor trucks", truckRows.size());

        List<VektorTrailerRow> trailerRows = this.vektorTrailerClient.fetchTrailers(jwt, companyId).stream()
                .map(this.vektorTrailerMapper::toRow)
                .toList();
        this.vektorTrailerRepository.replaceAll(trailerRows);
        log.info("execute::stored {} vektor trailers", trailerRows.size());

        return RepeatStatus.FINISHED;
    }
}
