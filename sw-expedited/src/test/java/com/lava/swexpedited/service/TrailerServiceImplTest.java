package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.SamsaraTrailerRepository;
import com.lava.swexpedited.repository.VektorDriverRepository;
import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.samsara.SamsaraTrailerRow;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
import com.lava.swexpedited.vektor.VektorDriverRow;
import com.lava.swexpedited.vektor.VektorTrailerRow;
import com.lava.swexpedited.vektor.VektorTruckRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrailerServiceImplTest {

    @Mock
    private VektorTrailerRepository vektorTrailerRepository;

    @Mock
    private VektorTruckRepository vektorTruckRepository;

    @Mock
    private VektorDriverRepository vektorDriverRepository;

    @Mock
    private SamsaraTrailerRepository samsaraTrailerRepository;

    @Test
    void findAll_trailerClaimedByTruck_includesTruckNumber() {
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of(truckRow("truck-1", "T1000", "trailer-1", null)));

        List<TrailerListingRow> result = service().findAll();

        assertThat(result).hasSize(1);
        TrailerListingRow row = result.getFirst();
        assertThat(row.id()).isEqualTo("trailer-1");
        assertThat(row.label()).isEqualTo("T231 - 53' SDL");
        assertThat(row.currentTruckNumber()).isEqualTo("T1000");
    }

    @Test
    void findAll_trailerWithNoClaimingTruck_currentTruckNumberIsNull() {
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of());

        List<TrailerListingRow> result = service().findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().currentTruckNumber()).isNull();
    }

    @Test
    void findDetail_unknownTrailerId_isEmpty() {
        when(this.vektorTrailerRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThat(service().findDetail("unknown")).isEmpty();
    }

    @Test
    void findDetail_trailerWithNoClaimingTruck_currentTruckAndDriverAreNull() {
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of());

        Optional<TrailerDetailResponse> result = service().findDetail("trailer-1");

        assertThat(result).isPresent();
        TrailerDetailResponse response = result.get();
        assertThat(response.id()).isEqualTo("trailer-1");
        assertThat(response.label()).isEqualTo("T231 - 53' SDL");
        assertThat(response.manufacturer()).isEqualTo("Great Dane");
        assertThat(response.year()).isEqualTo(2022);
        assertThat(response.currentTruckNumber()).isNull();
        assertThat(response.currentDriverName()).isNull();
    }

    @Test
    void findDetail_trailerClaimedByTruckWithDriver_resolvesTruckNumberAndDriverName() {
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll())
                .thenReturn(List.of(truckRow("truck-1", "T1000", "trailer-1", "driver-1")));
        when(this.vektorDriverRepository.findById("driver-1"))
                .thenReturn(Optional.of(driverRow("driver-1", "Jane Trucker")));

        Optional<TrailerDetailResponse> result = service().findDetail("trailer-1");

        assertThat(result).isPresent();
        TrailerDetailResponse response = result.get();
        assertThat(response.currentTruckNumber()).isEqualTo("T1000");
        assertThat(response.currentDriverName()).isEqualTo("Jane Trucker");
    }

    @Test
    void findDetail_trailerWithNoMatchedSamsaraTrailer_licensePlateAndAssetSerialNumberAreNull() {
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of());

        Optional<TrailerDetailResponse> result = service().findDetail("trailer-1");

        assertThat(result).isPresent();
        TrailerDetailResponse response = result.get();
        assertThat(response.vin()).isEqualTo("5MC125315H5165489");
        assertThat(response.licensePlate()).isNull();
        assertThat(response.assetSerialNumber()).isNull();
    }

    @Test
    void findDetail_trailerWithMatchedSamsaraTrailer_includesLicensePlateAndAssetSerialNumber() {
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(new VektorTrailerRow(
                        "trailer-1",
                        "T231 - 53' SDL",
                        "Great Dane",
                        2022,
                        "5MC125315H5165489",
                        "{}",
                        LocalDateTime.now(),
                        "samsara-trailer-1")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of());
        when(this.samsaraTrailerRepository.findById("samsara-trailer-1"))
                .thenReturn(Optional.of(new SamsaraTrailerRow(
                        "samsara-trailer-1",
                        "5MC125315H5165489",
                        "1704 - 53' SDL",
                        "34A1W4",
                        "5MC125315H5165489",
                        "{}",
                        LocalDateTime.now())));

        Optional<TrailerDetailResponse> result = service().findDetail("trailer-1");

        assertThat(result).isPresent();
        TrailerDetailResponse response = result.get();
        assertThat(response.licensePlate()).isEqualTo("34A1W4");
        assertThat(response.assetSerialNumber()).isEqualTo("5MC125315H5165489");
    }

    private TrailerServiceImpl service() {
        return new TrailerServiceImpl(
                this.vektorTrailerRepository,
                this.vektorTruckRepository,
                this.vektorDriverRepository,
                this.samsaraTrailerRepository);
    }

    private VektorTrailerRow trailerRow(String id, String label) {
        return new VektorTrailerRow(
                id, label, "Great Dane", 2022, "5MC125315H5165489", "{}", LocalDateTime.now(), null);
    }

    private VektorTruckRow truckRow(String id, String truckNumber, String currentTrailerId, String currentDriverId) {
        return new VektorTruckRow(
                id,
                truckNumber,
                1,
                "1FUJA6CV12LM12345",
                "Freightliner",
                "Cascadia",
                2023,
                currentTrailerId,
                currentDriverId,
                "{}",
                LocalDateTime.now(),
                null);
    }

    private VektorDriverRow driverRow(String id, String fullName) {
        return new VektorDriverRow(
                id, "D1000", fullName, "jane@example.com", "555-0100", null, "{}", LocalDateTime.now());
    }
}
