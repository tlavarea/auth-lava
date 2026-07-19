package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorManifestMapper;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class VektorSyncTaskletTest {

    @Mock
    private VektorAuthenticator vektorAuthenticator;

    @Mock
    private VektorManifestClient vektorManifestClient;

    @Mock
    private VektorDriverClient vektorDriverClient;

    @Mock
    private VektorManifestMapper vektorManifestMapper;

    @Mock
    private VektorDriverMatchStrategy vektorDriverMatchStrategy;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    private final VektorProperties vektorProperties = new VektorProperties(
            "user@example.com",
            "hunter2",
            "test-company-id",
            "https://app.vektortms.com",
            Duration.ofSeconds(5),
            List.of("manifest_in_progress"));

    @Test
    void execute_authenticatesFetchesMapsMatchesAndReplaces() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        VektorGrpcWeb.Message rawManifest = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(2, 1000589L)));
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"), eq("test-company-id"), eq(List.of("manifest_in_progress"))))
                .thenReturn(List.of(rawManifest));
        Map<String, String> driverNamesById = Map.of("driver-uuid", "Warren Ruawhare");
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(driverNamesById);
        VektorManifestRow mappedRow = row(1000589L, null);
        when(this.vektorManifestMapper.toRow(rawManifest, driverNamesById)).thenReturn(mappedRow);
        SamsaraDriverRow samsaraDriver = new SamsaraDriverRow(
                "samsara-1", "Warren Ruawhare", null, null, null, null, null, "active", null, null, null, "{}", null);
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(samsaraDriver));
        when(this.vektorDriverMatchStrategy.match(mappedRow, List.of(samsaraDriver)))
                .thenReturn(Optional.of("samsara-1"));

        VektorSyncTasklet tasklet = new VektorSyncTasklet(
                this.vektorAuthenticator,
                this.vektorManifestClient,
                this.vektorDriverClient,
                this.vektorManifestMapper,
                this.vektorDriverMatchStrategy,
                this.samsaraDriverRepository,
                this.vektorManifestRepository,
                this.vektorProperties);

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        ArgumentCaptor<List<VektorManifestRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorManifestRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(1);
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isEqualTo("samsara-1");
        assertThat(rowsCaptor.getValue().getFirst().manifestNumber()).isEqualTo(1000589L);
    }

    @Test
    void execute_noDriverMatch_leavesMatchedSamsaraDriverIdNull() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        VektorGrpcWeb.Message rawManifest = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(2, 1000589L)));
        when(this.vektorManifestClient.fetchManifests(eq("test-jwt"), eq("test-company-id"), anyList()))
                .thenReturn(List.of(rawManifest));
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(Map.of());
        VektorManifestRow mappedRow = row(1000589L, null);
        when(this.vektorManifestMapper.toRow(eq(rawManifest), Mockito.anyMap())).thenReturn(mappedRow);
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorDriverMatchStrategy.match(eq(mappedRow), Mockito.anyList()))
                .thenReturn(Optional.empty());

        VektorSyncTasklet tasklet = new VektorSyncTasklet(
                this.vektorAuthenticator,
                this.vektorManifestClient,
                this.vektorDriverClient,
                this.vektorManifestMapper,
                this.vektorDriverMatchStrategy,
                this.samsaraDriverRepository,
                this.vektorManifestRepository,
                this.vektorProperties);

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorManifestRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorManifestRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isNull();
    }

    private VektorManifestRow row(long manifestNumber, String matchedSamsaraDriverId) {
        return new VektorManifestRow(
                manifestNumber,
                "manifest-uuid",
                "driver-uuid",
                "Warren Ruawhare",
                matchedSamsaraDriverId,
                "manifest_in_progress",
                "Bessemer, AL",
                "Litchfield Park, AZ",
                null,
                null,
                null,
                null,
                "SwX-1000589",
                List.of(),
                null,
                "{}",
                null);
    }
}
