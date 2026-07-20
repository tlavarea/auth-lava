package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.vektor.VektorSessionProvider;
import com.lava.swexpedited.batch.vektor.VektorTruckEtaStatesClient;
import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.manifest.ManifestEtaResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.StopType;
import com.lava.swexpedited.vektor.VektorEtaSnapshotRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import com.lava.swexpedited.vektor.VektorManifestStop;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManifestEtaServiceImplTest {

    private static final String ACTIVE_STOP_ID = "78c13acf-de43-4658-b3d4-efbf34a57b5c";
    private static final String MANIFEST_ID = "0e2bb5f0-d639-4ce4-b9bd-73c5ec34a20c";

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private VektorSessionProvider vektorSessionProvider;

    @Mock
    private VektorTruckEtaStatesClient vektorTruckEtaStatesClient;

    private final VektorProperties vektorProperties = new VektorProperties(
            "user", "pass", "test-company-id", "https://example.com", Duration.ofSeconds(5), List.of(), 14, 60);

    @Test
    void findEta_manifestNotFound_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L)).thenReturn(Optional.empty());
        ManifestEtaServiceImpl service = service();

        assertThat(service.findEta(1000588L)).isEmpty();
    }

    @Test
    void findEta_allStopsCheckedOut_isEmptyWithoutCallingVektor() {
        VektorManifestStop completedStop = stop(1, LocalDateTime.of(2026, 7, 18, 9, 0));
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(List.of(completedStop))));
        ManifestEtaServiceImpl service = service();

        assertThat(service.findEta(1000588L)).isEmpty();

        verifyNoInteractions(this.vektorTruckEtaStatesClient);
    }

    @Test
    void findEta_noSnapshotTargetsTheActiveStop_isEmpty() {
        VektorManifestStop activeStop = stop(5, null);
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(List.of(activeStop))));
        stubWithSession();
        when(this.vektorTruckEtaStatesClient.fetch("test-jwt", "test-company-id", MANIFEST_ID))
                .thenReturn(
                        List.of(snapshot("some-other-stop-id", 5, 552.86, 567, LocalDateTime.of(2026, 7, 19, 2, 16))));
        ManifestEtaServiceImpl service = service();

        assertThat(service.findEta(1000588L)).isEmpty();
    }

    @Test
    void findEta_multipleSnapshotsForActiveStop_takesTheLastOneInResponseOrder() {
        VektorManifestStop activeStop = stop(5, null);
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(List.of(activeStop))));
        stubWithSession();
        when(this.vektorTruckEtaStatesClient.fetch("test-jwt", "test-company-id", MANIFEST_ID))
                .thenReturn(List.of(
                        snapshot(ACTIVE_STOP_ID, 5, 827.49, 857, LocalDateTime.of(2026, 7, 18, 23, 18)),
                        snapshot(ACTIVE_STOP_ID, 5, 552.86, 567, LocalDateTime.of(2026, 7, 19, 2, 16))));
        ManifestEtaServiceImpl service = service();

        Optional<ManifestEtaResponse> result = service.findEta(1000588L);

        assertThat(result).isPresent();
        // Matches the second (later, real dispatch-sheet-confirmed) snapshot, not the first.
        assertThat(result.get().remainingMiles()).isEqualByComparingTo(new BigDecimal("552.86"));
        assertThat(result.get().remainingMinutes()).isEqualTo(567);
        assertThat(result.get().estimatedArrival()).isEqualTo(LocalDateTime.of(2026, 7, 19, 2, 16));
    }

    @Test
    void findEta_found_mapsStopSequenceNumberFromTheSnapshot() {
        VektorManifestStop activeStop = stop(5, null);
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(List.of(activeStop))));
        stubWithSession();
        when(this.vektorTruckEtaStatesClient.fetch("test-jwt", "test-company-id", MANIFEST_ID))
                .thenReturn(List.of(snapshot(ACTIVE_STOP_ID, 5, 552.86, 567, LocalDateTime.of(2026, 7, 19, 2, 16))));
        ManifestEtaServiceImpl service = service();

        Optional<ManifestEtaResponse> result = service.findEta(1000588L);

        assertThat(result).isPresent();
        assertThat(result.get().stopSequenceNumber()).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    private void stubWithSession() {
        when(this.vektorSessionProvider.withSession(any())).thenAnswer(invocation -> {
            Function<String, ?> call = invocation.getArgument(0);
            return call.apply("test-jwt");
        });
    }

    private VektorEtaSnapshotRow snapshot(
            String targetStopId,
            int sequenceNumber,
            double remainingMiles,
            int remainingMinutes,
            LocalDateTime estimatedArrival) {
        return new VektorEtaSnapshotRow(
                targetStopId,
                sequenceNumber,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(remainingMiles),
                remainingMinutes,
                estimatedArrival);
    }

    private VektorManifestStop stop(int sequenceNumber, LocalDateTime checkedOutAt) {
        return new VektorManifestStop(
                ACTIVE_STOP_ID,
                sequenceNumber,
                StopType.DROPOFF,
                "Site",
                "Address",
                null,
                null,
                "CDT",
                LocalDateTime.of(2026, 7, 20, 8, 0),
                LocalDateTime.of(2026, 7, 21, 15, 0),
                null,
                null,
                checkedOutAt,
                "CO 03748983",
                null,
                null,
                null,
                null,
                null);
    }

    private ManifestEtaServiceImpl service() {
        return new ManifestEtaServiceImpl(
                this.vektorManifestRepository,
                this.vektorSessionProvider,
                this.vektorTruckEtaStatesClient,
                this.vektorProperties);
    }

    private VektorManifestRow manifestRow(List<VektorManifestStop> stops) {
        return new VektorManifestRow(
                1000588L,
                MANIFEST_ID,
                "019c76aa-28e6-4274-b9da-2923cc6949e9",
                "Michael Goodson",
                "truck-uuid",
                null,
                "manifest_in_progress",
                "origin",
                "destination",
                null,
                null,
                null,
                null,
                "SwX-1000588",
                stops,
                null,
                "{}",
                null);
    }
}
