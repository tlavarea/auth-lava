package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.repository.VektorTrailerRepository;
import com.lava.swexpedited.repository.VektorTruckRepository;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
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

    @Test
    void findAll_trailerClaimedByTruck_includesTruckNumber() {
        when(this.vektorTrailerRepository.findAll()).thenReturn(List.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        when(this.vektorTruckRepository.findAll()).thenReturn(List.of(truckRow("truck-1", "T1000", "trailer-1")));
        TrailerServiceImpl service = new TrailerServiceImpl(this.vektorTrailerRepository, this.vektorTruckRepository);

        List<TrailerListingRow> result = service.findAll();

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
        TrailerServiceImpl service = new TrailerServiceImpl(this.vektorTrailerRepository, this.vektorTruckRepository);

        List<TrailerListingRow> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().currentTruckNumber()).isNull();
    }

    @Test
    void findDetail_unknownTrailerId_isEmpty() {
        when(this.vektorTrailerRepository.findById("unknown")).thenReturn(Optional.empty());
        TrailerServiceImpl service = new TrailerServiceImpl(this.vektorTrailerRepository, this.vektorTruckRepository);

        assertThat(service.findDetail("unknown")).isEmpty();
    }

    @Test
    void findDetail_knownTrailerId_returnsDetail() {
        when(this.vektorTrailerRepository.findById("trailer-1"))
                .thenReturn(Optional.of(trailerRow("trailer-1", "T231 - 53' SDL")));
        TrailerServiceImpl service = new TrailerServiceImpl(this.vektorTrailerRepository, this.vektorTruckRepository);

        Optional<TrailerDetailResponse> result = service.findDetail("trailer-1");

        assertThat(result).isPresent();
        TrailerDetailResponse response = result.get();
        assertThat(response.id()).isEqualTo("trailer-1");
        assertThat(response.label()).isEqualTo("T231 - 53' SDL");
        assertThat(response.manufacturer()).isEqualTo("Great Dane");
        assertThat(response.year()).isEqualTo(2022);
    }

    private VektorTrailerRow trailerRow(String id, String label) {
        return new VektorTrailerRow(id, label, "Great Dane", 2022, "{}", LocalDateTime.now());
    }

    private VektorTruckRow truckRow(String id, String truckNumber, String currentTrailerId) {
        return new VektorTruckRow(
                id,
                truckNumber,
                1,
                "1FUJA6CV12LM12345",
                "Freightliner",
                "Cascadia",
                2023,
                currentTrailerId,
                null,
                "{}",
                LocalDateTime.now());
    }
}
