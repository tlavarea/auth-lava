package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private VektorTimeOffClient vektorTimeOffClient;

    @Mock
    private VektorManifestMapper vektorManifestMapper;

    @Mock
    private VektorTimeOffMapper vektorTimeOffMapper;

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private VektorTimeOffRepository vektorTimeOffRepository;

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private VektorDriverRepository vektorDriverRepository;

    private final VektorProperties vektorProperties = new VektorProperties(
            "user@example.com",
            "hunter2",
            "test-company-id",
            "https://app.vektortms.com",
            Duration.ofSeconds(5),
            List.of("manifest_in_progress"),
            14,
            60);

    @Test
    void execute_authenticatesFetchesMapsMatchesAndReplaces() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        VektorGrpcWeb.Message rawManifest = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(2, 1000589L)));
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"),
                        eq("test-company-id"),
                        eq(List.of("manifest_in_progress")),
                        any(LocalDate.class),
                        any(LocalDate.class)))
                .thenReturn(List.of(rawManifest));
        Map<String, String> driverNamesById = Map.of("driver-uuid", "Warren Ruawhare");
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(driverNamesById);
        VektorManifestRow mappedRow = row(1000589L, null);
        when(this.vektorManifestMapper.toRow(rawManifest, driverNamesById)).thenReturn(mappedRow);
        when(this.vektorDriverRepository.findMatchedSamsaraDriverIdById())
                .thenReturn(Map.of("driver-uuid", "samsara-1"));

        VektorSyncTasklet tasklet = tasklet();

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        ArgumentCaptor<List<VektorManifestRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorManifestRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(1);
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isEqualTo("samsara-1");
        assertThat(rowsCaptor.getValue().getFirst().manifestNumber()).isEqualTo(1000589L);
        Mockito.verify(this.vektorManifestRepository).pruneSupersededManifests(List.of(1000589L));
    }

    @Test
    void execute_manifestDriverIdNotInMatchedMap_leavesMatchedSamsaraDriverIdNull() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        VektorGrpcWeb.Message rawManifest = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(2, 1000589L)));
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"), eq("test-company-id"), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(rawManifest));
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(Map.of());
        VektorManifestRow mappedRow = row(1000589L, null);
        when(this.vektorManifestMapper.toRow(eq(rawManifest), Mockito.anyMap())).thenReturn(mappedRow);
        when(this.vektorDriverRepository.findMatchedSamsaraDriverIdById()).thenReturn(Map.of());

        VektorSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorManifestRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorManifestRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isNull();
    }

    @Test
    void execute_timeOffEntryForKnownTruck_resolvesDriverAndUpserts() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"), eq("test-company-id"), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(Map.of("driver-uuid", "Warren Ruawhare"));
        VektorGrpcWeb.Message rawTimeOff = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(2, "time-off-uuid")));
        when(this.vektorTimeOffClient.fetchTimeOff(eq("test-jwt"), eq("test-company-id"), any(LocalDate.class)))
                .thenReturn(List.of(rawTimeOff));
        VektorTimeOffRow mappedTimeOff = timeOffRow("time-off-uuid", "truck-uuid", null);
        when(this.vektorTimeOffMapper.toRow(rawTimeOff)).thenReturn(mappedTimeOff);
        when(this.vektorTruckRepository.findCurrentDriverIdByTruckId()).thenReturn(Map.of("truck-uuid", "driver-uuid"));
        when(this.vektorDriverRepository.findMatchedSamsaraDriverIdById())
                .thenReturn(Map.of("driver-uuid", "samsara-1"));

        VektorSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTimeOffRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTimeOffRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(1);
        assertThat(rowsCaptor.getValue().getFirst().id()).isEqualTo("time-off-uuid");
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isEqualTo("samsara-1");
    }

    @Test
    void execute_timeOffEntryForUnknownTruck_leavesMatchedSamsaraDriverIdNull() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"), eq("test-company-id"), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(Map.of());
        VektorGrpcWeb.Message rawTimeOff = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(2, "time-off-uuid")));
        when(this.vektorTimeOffClient.fetchTimeOff(eq("test-jwt"), eq("test-company-id"), any(LocalDate.class)))
                .thenReturn(List.of(rawTimeOff));
        VektorTimeOffRow mappedTimeOff = timeOffRow("time-off-uuid", "truck-uuid", null);
        when(this.vektorTimeOffMapper.toRow(rawTimeOff)).thenReturn(mappedTimeOff);
        when(this.vektorTruckRepository.findCurrentDriverIdByTruckId()).thenReturn(Map.of());

        VektorSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTimeOffRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTimeOffRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isNull();
    }

    @Test
    void execute_timeOffTruckKnownButDriverNotMatched_leavesMatchedSamsaraDriverIdNull() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorManifestClient.fetchManifests(
                        eq("test-jwt"), eq("test-company-id"), anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(this.vektorDriverClient.fetchDriverNamesById("test-jwt", "test-company-id"))
                .thenReturn(Map.of());
        VektorGrpcWeb.Message rawTimeOff = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(2, "time-off-uuid")));
        when(this.vektorTimeOffClient.fetchTimeOff(eq("test-jwt"), eq("test-company-id"), any(LocalDate.class)))
                .thenReturn(List.of(rawTimeOff));
        VektorTimeOffRow mappedTimeOff = timeOffRow("time-off-uuid", "truck-uuid", null);
        when(this.vektorTimeOffMapper.toRow(rawTimeOff)).thenReturn(mappedTimeOff);
        when(this.vektorTruckRepository.findCurrentDriverIdByTruckId()).thenReturn(Map.of("truck-uuid", "driver-uuid"));
        when(this.vektorDriverRepository.findMatchedSamsaraDriverIdById()).thenReturn(Map.of());

        VektorSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTimeOffRow>> rowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTimeOffRepository).upsertAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue().getFirst().matchedSamsaraDriverId()).isNull();
    }

    private VektorSyncTasklet tasklet() {
        return new VektorSyncTasklet(
                this.vektorAuthenticator,
                this.vektorManifestClient,
                this.vektorDriverClient,
                this.vektorTimeOffClient,
                this.vektorManifestMapper,
                this.vektorTimeOffMapper,
                this.vektorManifestRepository,
                this.vektorTimeOffRepository,
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.vektorProperties);
    }

    private VektorManifestRow row(long manifestNumber, String matchedSamsaraDriverId) {
        return new VektorManifestRow(
                manifestNumber,
                "manifest-uuid",
                "driver-uuid",
                "Warren Ruawhare",
                "truck-uuid",
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

    private VektorTimeOffRow timeOffRow(String id, String truckId, String matchedSamsaraDriverId) {
        return new VektorTimeOffRow(
                id,
                truckId,
                matchedSamsaraDriverId,
                LocalDateTime.of(2026, 7, 17, 0, 0),
                LocalDateTime.of(2026, 7, 18, 0, 0),
                "Vacation",
                "{}",
                null);
    }
}
