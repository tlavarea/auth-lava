package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.vektor.VektorEntityLocationClient;
import com.lava.swexpedited.batch.vektor.VektorSessionProvider;
import com.lava.swexpedited.boot.autoconfigure.app.VektorProperties;
import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorDriverLocationRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
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
class ManifestDriverLocationServiceImplTest {

    private static final String DRIVER_ID = "019c76aa-28e6-4274-b9da-2923cc6949e9";

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private VektorSessionProvider vektorSessionProvider;

    @Mock
    private VektorEntityLocationClient vektorEntityLocationClient;

    private final VektorProperties vektorProperties = new VektorProperties(
            "user", "pass", "test-company-id", "https://example.com", Duration.ofSeconds(5), List.of());

    @Test
    void findLiveLocation_manifestNotFound_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L)).thenReturn(Optional.empty());
        ManifestDriverLocationServiceImpl service = service();

        assertThat(service.findLiveLocation(1000588L)).isEmpty();
    }

    @Test
    void findLiveLocation_manifestHasNoDriverId_isEmptyWithoutCallingVektor() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L)).thenReturn(Optional.of(manifestRow(null)));
        ManifestDriverLocationServiceImpl service = service();

        assertThat(service.findLiveLocation(1000588L)).isEmpty();
    }

    @Test
    void findLiveLocation_noMatchingDriverInResponse_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(DRIVER_ID)));
        stubWithSession();
        when(this.vektorEntityLocationClient.fetchAll("test-jwt", "test-company-id"))
                .thenReturn(List.of(locationRow("some-other-driver", LocalDateTime.of(2026, 7, 19, 2, 0))));
        ManifestDriverLocationServiceImpl service = service();

        assertThat(service.findLiveLocation(1000588L)).isEmpty();
    }

    @Test
    void findLiveLocation_multipleEntriesForDriver_picksTheMostRecentByAsOf() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(DRIVER_ID)));
        stubWithSession();
        when(this.vektorEntityLocationClient.fetchAll("test-jwt", "test-company-id"))
                .thenReturn(List.of(
                        locationRow(DRIVER_ID, LocalDateTime.of(2026, 7, 18, 9, 0)),
                        locationRow(DRIVER_ID, LocalDateTime.of(2026, 7, 19, 2, 18))));
        ManifestDriverLocationServiceImpl service = service();

        Optional<ManifestDriverLocationResponse> result = service.findLiveLocation(1000588L);

        assertThat(result).isPresent();
        assertThat(result.get().asOf()).isEqualTo(LocalDateTime.of(2026, 7, 19, 2, 18));
    }

    @Test
    void findLiveLocation_found_mapsAllFields() {
        when(this.vektorManifestRepository.findByManifestNumber(1000588L))
                .thenReturn(Optional.of(manifestRow(DRIVER_ID)));
        stubWithSession();
        when(this.vektorEntityLocationClient.fetchAll("test-jwt", "test-company-id"))
                .thenReturn(List.of(new VektorDriverLocationRow(
                        DRIVER_ID,
                        new BigDecimal("30.4183333"),
                        new BigDecimal("-89.1889962"),
                        new BigDecimal("294.91"),
                        LocalDateTime.of(2026, 7, 19, 2, 18, 7),
                        "Long Beach, MS")));
        ManifestDriverLocationServiceImpl service = service();

        Optional<ManifestDriverLocationResponse> result = service.findLiveLocation(1000588L);

        assertThat(result).isPresent();
        ManifestDriverLocationResponse response = result.get();
        assertThat(response.latitude()).isEqualByComparingTo(new BigDecimal("30.4183333"));
        assertThat(response.longitude()).isEqualByComparingTo(new BigDecimal("-89.1889962"));
        assertThat(response.headingDegrees()).isEqualByComparingTo(new BigDecimal("294.91"));
        assertThat(response.formattedLocation()).isEqualTo("Long Beach, MS");
    }

    @SuppressWarnings("unchecked")
    private void stubWithSession() {
        when(this.vektorSessionProvider.withSession(any())).thenAnswer(invocation -> {
            Function<String, ?> call = invocation.getArgument(0);
            return call.apply("test-jwt");
        });
    }

    private VektorDriverLocationRow locationRow(String driverId, LocalDateTime asOf) {
        return new VektorDriverLocationRow(driverId, BigDecimal.ZERO, BigDecimal.ZERO, null, asOf, null);
    }

    private ManifestDriverLocationServiceImpl service() {
        return new ManifestDriverLocationServiceImpl(
                this.vektorManifestRepository,
                this.vektorSessionProvider,
                this.vektorEntityLocationClient,
                this.vektorProperties);
    }

    private VektorManifestRow manifestRow(String driverId) {
        return new VektorManifestRow(
                1000588L,
                "0e2bb5f0-d639-4ce4-b9bd-73c5ec34a20c",
                driverId,
                "Michael Goodson",
                null,
                "manifest_in_progress",
                "origin",
                "destination",
                null,
                null,
                null,
                null,
                "SwX-1000588",
                List.of(),
                null,
                "{}",
                null);
    }
}
