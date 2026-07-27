package com.lava.swexpedited.batch.vektor;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraVehicleRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import com.lava.swexpedited.vektor.VektorDriverMapper;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerMapper;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckMapper;
import com.lava.swexpedited.vektor.VektorTruckMatchStrategy;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.util.LinkedHashMap;
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
 * Logs into Vektor once, then fetches and replaces the full driver/truck/trailer rosters in that order - a separate job
 * from {@link VektorSyncTasklet} (manifests/time-off) rather than a step chained onto it, since
 * {@code VektorAuthenticator#authenticate()} is already called fresh per job today with no shared-session plumbing
 * across jobs, and there's no ordering requirement between the two: {@code VektorSyncTasklet}'s driver-matching just
 * reads whatever this job last synced, tolerating staleness the same way independently-synced tables are already joined
 * elsewhere in this app (see {@code DriverTimelineServiceImpl}). Drivers are matched against the Samsara roster here,
 * once per driver, via {@link VektorDriverMatchStrategy} - this replaces the old approach of {@code VektorSyncTasklet}
 * re-running that same match live against every individual manifest/time-off row. A single tasklet, not chunked - same
 * reasoning as {@code VektorSyncTasklet}: a handful of bulk HTTP calls total per sync, not one per item. Trucks are
 * matched against the Samsara vehicle roster the same way, once per truck, via {@link VektorTruckMatchStrategy} - after
 * first being deduplicated by VIN (see {@link #dedupeByVin}), since {@code Trucks/Get} has been observed returning the
 * same physical truck under two different ids.
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
    private final VektorTruckMatchStrategy vektorTruckMatchStrategy;
    private final SamsaraDriverRepository samsaraDriverRepository;
    private final SamsaraVehicleRepository samsaraVehicleRepository;
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
            VektorTruckMatchStrategy vektorTruckMatchStrategy,
            SamsaraDriverRepository samsaraDriverRepository,
            SamsaraVehicleRepository samsaraVehicleRepository,
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
        this.vektorTruckMatchStrategy = vektorTruckMatchStrategy;
        this.samsaraDriverRepository = samsaraDriverRepository;
        this.samsaraVehicleRepository = samsaraVehicleRepository;
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

        List<SamsaraVehicleRow> samsaraVehicles = this.samsaraVehicleRepository.findAll();
        List<VektorTruckRow> truckRows = dedupeByVin(this.vektorTruckClient.fetchTrucks(jwt, companyId).stream()
                        .map(this.vektorTruckMapper::toRow)
                        .toList())
                .stream()
                .map(row -> this.vektorTruckMatchStrategy
                        .match(row.vin(), samsaraVehicles)
                        .map(row::withMatchedSamsaraVehicleId)
                        .orElse(row))
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

    /**
     * Collapses rows that share a VIN into one. {@code Trucks/Get} has been observed returning two distinct truck_ids
     * for the same physical truck - same VIN and truck_number, one fully populated (current driver/trailer assignment
     * plus every other captured field) and one sparse (little beyond id/truck_number/VIN/make/model/year) - a data
     * artifact on Vektor's side this app can't fix upstream, so it's collapsed here instead of surfacing both as
     * separate trucks. Keeps whichever row of a VIN group has a live driver/trailer assignment
     * ({@link VektorTruckMapper}'s fields 16/17 - Vektor's own confirmed "this truck is in active use" signal); if
     * neither or both do, keeps whichever was encountered first. Rows with a null VIN are keyed on their own truck_id
     * instead, since there's no other safe key to group them on.
     */
    private List<VektorTruckRow> dedupeByVin(List<VektorTruckRow> rows) {
        Map<String, VektorTruckRow> canonicalByKey = new LinkedHashMap<>();
        for (VektorTruckRow row : rows) {
            String key = row.vin() != null ? row.vin() : row.id();
            VektorTruckRow existing = canonicalByKey.get(key);
            if (existing == null || (!hasActiveAssignment(existing) && hasActiveAssignment(row))) {
                canonicalByKey.put(key, row);
            }
        }
        return List.copyOf(canonicalByKey.values());
    }

    private boolean hasActiveAssignment(VektorTruckRow row) {
        return row.currentDriverId() != null || row.currentTrailerId() != null;
    }
}
