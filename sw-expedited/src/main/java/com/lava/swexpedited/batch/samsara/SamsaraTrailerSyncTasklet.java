package com.lava.swexpedited.batch.samsara;

import com.lava.swexpedited.repository.SamsaraTrailerRepository;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import com.lava.swexpedited.samsara.SamsaraTrailerWithRaw;
import com.lava.swexpedited.samsara.model.Trailer;
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
 * Fetches Samsara's full trailer roster and replaces samsara_trailer. Exists so {@code VinMatchingTrailerMatchStrategy}
 * has a vin -&gt; Samsara trailer id lookup to match against vektor_trailer.vin - independent of
 * {@link SamsaraVehicleSyncTasklet}/{@link SamsaraDriverSyncTasklet}'s tables (no FK either direction).
 */
@Component
@Slf4j
public class SamsaraTrailerSyncTasklet extends SamsaraTasklet implements Tasklet {

    private final SamsaraFleetClient samsaraFleetClient;
    private final SamsaraTrailerRepository samsaraTrailerRepository;

    public SamsaraTrailerSyncTasklet(
            SamsaraFleetClient samsaraFleetClient, SamsaraTrailerRepository samsaraTrailerRepository) {
        this.samsaraFleetClient = samsaraFleetClient;
        this.samsaraTrailerRepository = samsaraTrailerRepository;
    }

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<SamsaraTrailerRow> rows = this.samsaraFleetClient.fetchTrailers().stream()
                .map(SamsaraTrailerSyncTasklet::toRow)
                .toList();
        this.samsaraTrailerRepository.replaceAll(rows);
        log.info("execute::stored {} samsara trailers", rows.size());
        return RepeatStatus.FINISHED;
    }

    /**
     * Maps one {@code /fleet/trailers} entry, paired with its captured raw JSON, to a samsara_trailer row.
     *
     * @param trailerWithRaw - the Samsara trailer payload and its captured raw JSON as a {@link SamsaraTrailerWithRaw}
     *     object.
     * @return the trailer data transformed to a {@link SamsaraTrailerRow} object.
     */
    private static SamsaraTrailerRow toRow(SamsaraTrailerWithRaw trailerWithRaw) {
        Trailer payload = trailerWithRaw.payload();
        Map<String, String> externalIds = payload.getExternalIds();
        String vin = externalIds != null ? externalIds.get("samsara.vin") : null;
        return new SamsaraTrailerRow(
                payload.getId(),
                vin,
                payload.getName(),
                payload.getLicensePlate(),
                payload.getTrailerSerialNumber(),
                trailerWithRaw.rawJson(),
                null);
    }
}
