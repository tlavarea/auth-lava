package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.repository.SamsaraDriverRepository;
import com.lava.swexpedited.repository.SamsaraTrailerRepository;
import com.lava.swexpedited.repository.SamsaraVehicleRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraDriverRow;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import com.lava.swexpedited.samsara.SamsaraVehicleRow;
import com.lava.swexpedited.vektor.VektorDriverMapper;
import com.lava.swexpedited.vektor.VektorDriverMatchStrategy;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import com.lava.swexpedited.vektor.VektorTrailerMapper;
import com.lava.swexpedited.vektor.VektorTrailerMatchStrategy;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckMapper;
import com.lava.swexpedited.vektor.VektorTruckMatchStrategy;
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
    private VektorTruckMatchStrategy vektorTruckMatchStrategy;

    @Mock
    private VektorTrailerMatchStrategy vektorTrailerMatchStrategy;

    @Mock
    private SamsaraDriverRepository samsaraDriverRepository;

    @Mock
    private SamsaraVehicleRepository samsaraVehicleRepository;

    @Mock
    private SamsaraTrailerRepository samsaraTrailerRepository;

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
        VektorTruckRow mappedTruck = new VektorTruckRow(
                "truck-uuid", "2401", null, "1XPBD49X7ND764317", null, null, null, null, null, "{}", null, null);
        when(this.vektorTruckMapper.toRow(rawTruck)).thenReturn(mappedTruck);
        SamsaraVehicleRow samsaraVehicle = new SamsaraVehicleRow(
                "samsara-vehicle-1", "1XPBD49X7ND764317", "2401", null, null, null, null, "{}", null);
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of(samsaraVehicle));
        when(this.vektorTruckMatchStrategy.match("1XPBD49X7ND764317", List.of(samsaraVehicle)))
                .thenReturn(Optional.of("samsara-vehicle-1"));

        VektorGrpcWeb.Message rawTrailer = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid")));
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawTrailer));
        VektorTrailerRow mappedTrailer = new VektorTrailerRow(
                "trailer-uuid", "T231 - 53' SDL", null, null, "5MC125315H5165489", "{}", null, null);
        when(this.vektorTrailerMapper.toRow(rawTrailer)).thenReturn(mappedTrailer);
        SamsaraTrailerRow samsaraTrailer =
                new SamsaraTrailerRow("samsara-trailer-1", "5MC125315H5165489", "1704", null, null, "{}", null);
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of(samsaraTrailer));
        when(this.vektorTrailerMatchStrategy.match("5MC125315H5165489", List.of(samsaraTrailer)))
                .thenReturn(Optional.of("samsara-trailer-1"));

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
        assertThat(truckRowsCaptor.getValue().getFirst().matchedSamsaraVehicleId())
                .isEqualTo("samsara-vehicle-1");

        ArgumentCaptor<List<VektorTrailerRow>> trailerRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTrailerRepository).replaceAll(trailerRowsCaptor.capture());
        assertThat(trailerRowsCaptor.getValue())
                .extracting(VektorTrailerRow::id)
                .containsExactly("trailer-uuid");
        assertThat(trailerRowsCaptor.getValue().getFirst().matchedSamsaraTrailerId())
                .isEqualTo("samsara-trailer-1");
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
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of());

        VektorFleetSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorDriverRow>> driverRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorDriverRepository).replaceAll(driverRowsCaptor.capture());
        assertThat(driverRowsCaptor.getValue().getFirst().matchedSamsaraDriverId())
                .isNull();
    }

    @Test
    void execute_vektorReturnsSameVinTwiceSparseFirst_keepsTheRowWithAnActiveAssignment() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());

        VektorGrpcWeb.Message rawSparseTruck = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "truck-sparse")));
        VektorGrpcWeb.Message rawAssignedTruck = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "truck-assigned")));
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawSparseTruck, rawAssignedTruck));
        VektorTruckRow sparseTruck = new VektorTruckRow(
                "truck-sparse", "2201", 1, "1XK1D49X0NJ123612", "KENWORTH", "K100", 1992, null, null, "{}", null, null);
        VektorTruckRow assignedTruck = new VektorTruckRow(
                "truck-assigned",
                "2201",
                1,
                "1XK1D49X0NJ123612",
                "KENWORTH",
                "W990",
                2022,
                "trailer-1",
                "driver-1",
                "{}",
                null,
                null);
        when(this.vektorTruckMapper.toRow(rawSparseTruck)).thenReturn(sparseTruck);
        when(this.vektorTruckMapper.toRow(rawAssignedTruck)).thenReturn(assignedTruck);
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of());
        when(this.vektorTruckMatchStrategy.match("1XK1D49X0NJ123612", List.of()))
                .thenReturn(Optional.empty());

        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of());

        VektorFleetSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTruckRow>> truckRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTruckRepository).replaceAll(truckRowsCaptor.capture());
        assertThat(truckRowsCaptor.getValue()).extracting(VektorTruckRow::id).containsExactly("truck-assigned");
    }

    @Test
    void execute_vektorReturnsSameVinTwiceAssignedFirst_stillKeepsTheAssignedRow() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());

        VektorGrpcWeb.Message rawAssignedTruck = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "truck-assigned")));
        VektorGrpcWeb.Message rawSparseTruck = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "truck-sparse")));
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawAssignedTruck, rawSparseTruck));
        VektorTruckRow assignedTruck = new VektorTruckRow(
                "truck-assigned",
                "2201",
                1,
                "1XK1D49X0NJ123612",
                "KENWORTH",
                "W990",
                2022,
                "trailer-1",
                "driver-1",
                "{}",
                null,
                null);
        VektorTruckRow sparseTruck = new VektorTruckRow(
                "truck-sparse", "2201", 1, "1XK1D49X0NJ123612", "KENWORTH", "K100", 1992, null, null, "{}", null, null);
        when(this.vektorTruckMapper.toRow(rawAssignedTruck)).thenReturn(assignedTruck);
        when(this.vektorTruckMapper.toRow(rawSparseTruck)).thenReturn(sparseTruck);
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of());
        when(this.vektorTruckMatchStrategy.match("1XK1D49X0NJ123612", List.of()))
                .thenReturn(Optional.empty());

        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of());

        VektorFleetSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTruckRow>> truckRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTruckRepository).replaceAll(truckRowsCaptor.capture());
        assertThat(truckRowsCaptor.getValue()).extracting(VektorTruckRow::id).containsExactly("truck-assigned");
    }

    @Test
    void execute_vektorReturnsSameTrailerVinTwice_collapsesToOneRow() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id")).thenReturn(List.of());
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of());

        VektorGrpcWeb.Message rawFirstTrailer = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "trailer-first")));
        VektorGrpcWeb.Message rawSecondTrailer = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeString(1, "trailer-second")));
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of(rawFirstTrailer, rawSecondTrailer));
        VektorTrailerRow firstTrailer = new VektorTrailerRow(
                "trailer-first", "T231 - 53' SDL", null, null, "5MC125315H5165489", "{}", null, null);
        VektorTrailerRow secondTrailer = new VektorTrailerRow(
                "trailer-second", "T231 - 53' SDL", null, null, "5MC125315H5165489", "{}", null, null);
        when(this.vektorTrailerMapper.toRow(rawFirstTrailer)).thenReturn(firstTrailer);
        when(this.vektorTrailerMapper.toRow(rawSecondTrailer)).thenReturn(secondTrailer);
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerMatchStrategy.match("5MC125315H5165489", List.of()))
                .thenReturn(Optional.empty());

        VektorFleetSyncTasklet tasklet = tasklet();

        tasklet.execute(null, null);

        ArgumentCaptor<List<VektorTrailerRow>> trailerRowsCaptor = ArgumentCaptor.captor();
        Mockito.verify(this.vektorTrailerRepository).replaceAll(trailerRowsCaptor.capture());
        assertThat(trailerRowsCaptor.getValue())
                .extracting(VektorTrailerRow::id)
                .containsExactly("trailer-first");
    }

    @Test
    void execute_emptyResponses_replacesAllThreeTablesWithEmptyLists() {
        when(this.vektorAuthenticator.authenticate()).thenReturn("test-jwt");
        when(this.vektorDriverClient.fetchDrivers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraDriverRepository.findAll()).thenReturn(List.of());
        when(this.vektorTruckClient.fetchTrucks("test-jwt", "test-company-id")).thenReturn(List.of());
        when(this.samsaraVehicleRepository.findAll()).thenReturn(List.of());
        when(this.vektorTrailerClient.fetchTrailers("test-jwt", "test-company-id"))
                .thenReturn(List.of());
        when(this.samsaraTrailerRepository.findAll()).thenReturn(List.of());

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
                this.vektorTruckMatchStrategy,
                this.vektorTrailerMatchStrategy,
                this.samsaraDriverRepository,
                this.samsaraVehicleRepository,
                this.samsaraTrailerRepository,
                this.vektorDriverRepository,
                this.vektorTruckRepository,
                this.vektorTrailerRepository,
                this.vektorProperties);
    }
}
