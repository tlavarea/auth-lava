package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.vektor.VektorDriverMapper;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorTrailerMapper;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckMapper;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class VektorFleetSyncTaskletTest {

    @Mock
    private VektorAuthenticator vektorAuthenticator;

    @Mock
    private VektorDriverClient vektorDriverClient;

    @Mock
    private VektorTruckClient vektorTruckClient;

    @Mock
    private VektorTrailerClient vektorTrailerClient;

    @Mock
    private VektorDriverMapper vektorDriverMapper;

    @Mock
    private VektorTruckMapper vektorTruckMapper;

    @Mock
    private VektorTrailerMapper vektorTrailerMapper;

    @Mock
    private VektorDriverMatchStrategy vektorDriverMatchStrategy;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private VektorDriverRepository vektorDriverRepository;

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private VektorTrailerRepository vektorTrailerRepository;

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
    void execute_authenticatesFetchesMapsMatchesDriversAndReplacesAllThreeTables() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");

        VektorGrpcWeb.Message rawDriver = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "driver-uuid")));
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawDriver));
        VektorDriverRow mappedDriver =
                new VektorDriverRow("driver-uuid", "8325", "Warren Ruawhare", null, null, null, "{}", null);
        when(this.vektorDriverMapper.toRow(rawDriver)).thenReturn(mappedDriver);
        SamsaraDriverRow samsaraDriver = new SamsaraDriverRow(
                "samsara-1", "Warren Ruawhare", null, null, null, null, null, "active", null, null, null, "{}", null);
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of(samsaraDriver));
        when(this.vektorDriverMatchStrategy.match("Warren Ruawhare", List.of(samsaraDriver)))
                .thenReturn(Optional.of("samsara-1"));

        VektorGrpcWeb.Message rawTruck = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "truck-uuid")));
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id")).thenReturn(List.of(rawTruck));
        VektorTruckRow mappedTruck =
                new VektorTruckRow("truck-uuid", "2401", null, null, null, null, null, null, null, "{}", null);
        when(this.vektorTruckMapper.toRow(rawTruck)).thenReturn(mappedTruck);

        VektorGrpcWeb.Message rawTrailer = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid")));
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawTrailer));
        VektorTrailerRow mappedTrailer = new VektorTrailerRow("trailer-uuid", "T231 - 53' SDL", null, null, "{}", null);
        when(this.vektorTrailerMapper.toRow(rawTrailer)).thenReturn(mappedTrailer);

        VektorFleetSyncTasklet tasklet = tasklet();

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);

        ArgumentCaptor<List<VektorDriverRow>> driverRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorDriverRepository).replaceAll(driverRowsCaptor.capture());
        assertThat(driverRowsCaptor.getValue()).hasSize(1);
        assertThat(driverRowsCaptor.getValue().getFirst().matchedSamsaraDriverId())
                .isEqualTo("samsara-1");

        ArgumentCaptor<List<VektorTruckRow>> truckRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTruckRepository).replaceAll(truckRowsCaptor.capture());
        assertThat(truckRowsCaptor.getValue()).extracting(VektorTruckRow::id).containsExactly("truck-uuid");

        ArgumentCaptor<List<VektorTrailerRow>> trailerRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTrailerRepository).replaceAll(trailerRowsCaptor.capture());
        assertThat(trailerRowsCaptor.getValue())
                .extracting(VektorTrailerRow::id)
                .containsExactly("trailer-uuid");
    }

    @Test
    void execute_driverNameMatchesNoSamsaraDriver_leavesMatchedSamsaraDriverIdNull() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        VektorGrpcWeb.Message rawDriver = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "driver-uuid")));
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawDriver));
        VektorDriverRow mappedDriver =
                new VektorDriverRow("driver-uuid", null, "Warren Ruawhare", null, null, null, "{}", null);
        when(this.vektorDriverMapper.toRow(rawDriver)).thenReturn(mappedDriver);
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorDriverMatchStrategy.match("Warren Ruawhare", List.of())).thenReturn(Optional.empty());
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id")).thenReturn(List.of());
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());

        VektorFleetSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorDriverRow>> driverRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorDriverRepository).replaceAll(driverRowsCaptor.capture());
        assertThat(driverRowsCaptor.getValue().getFirst().matchedSamsaraDriverId())
                .isNull();
    }

    @Test
    void execute_emptyResponses_replacesAllThreeTablesWithEmptyLists() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id")).thenReturn(List.of());
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());

        VektorFleetSyncTasklet tasklet = tasklet();

        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        Mockito.verify(this.vektorDriverRepository).replaceAll(List.of());
        Mockito.verify(this.vektorTruckRepository).replaceAll(List.of());
        Mockito.verify(this.vektorTrailerRepository).replaceAll(List.of());
    }

    private VektorFleetSyncTasklet tasklet() {
        return new VektorFleetSyncTasklet(
                this.vektorAuthenticator,
                this.vektorDriverClient,
                this.vektorTruckClient,
                this.vektorTrailerClient,
                this.vektorDriverMapper,
                this.vektorTruckMapper,
                this.vektorTrailerMapper,
                this.vektorDriverMatchStrategy,
                this.samsaraDriverRepository,
                this.vektorDriverRepository,
                this.vektorTruckRepository,
                this.vektorTrailerRepository,
                this.vektorProperties);
    }
}
