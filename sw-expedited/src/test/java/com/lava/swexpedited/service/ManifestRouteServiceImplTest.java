package com.lava.swexpedited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient;
import com.lava.swexpedited.batch.pickupmatch.GoogleRoutesClient.ComputedRoute;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManifestRouteServiceImplTest {

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private GoogleRoutesClient googleRoutesClient;

    @Test
    void findRoute_manifestNotFound_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L)).thenReturn(Optional.empty());
        ManifestRouteServiceImpl service =
                new ManifestRouteServiceImpl(this.vektorManifestRepository, this.googleRoutesClient);

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_manifestWithoutDestinationCoordinates_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(null, null)));
        ManifestRouteServiceImpl service =
                new ManifestRouteServiceImpl(this.vektorManifestRepository, this.googleRoutesClient);

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_googleReturnsNoRoute_isEmpty() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(new BigDecimal("33.101"), new BigDecimal("-87.99"))));
        when(this.googleRoutesClient.computeRoute(
                        "4251 Turin Dr, Bessemer, AL 35020",
                        new LatLng(new BigDecimal("33.101"), new BigDecimal("-87.99"))))
                .thenReturn(Optional.empty());
        ManifestRouteServiceImpl service =
                new ManifestRouteServiceImpl(this.vektorManifestRepository, this.googleRoutesClient);

        assertThat(service.findRoute(1000589L)).isEmpty();
    }

    @Test
    void findRoute_routeExists_returnsResponse() {
        when(this.vektorManifestRepository.findByManifestNumber(1000589L))
                .thenReturn(Optional.of(manifestRow(new BigDecimal("33.101"), new BigDecimal("-87.99"))));
        when(this.googleRoutesClient.computeRoute(
                        "4251 Turin Dr, Bessemer, AL 35020",
                        new LatLng(new BigDecimal("33.101"), new BigDecimal("-87.99"))))
                .thenReturn(Optional.of(new ComputedRoute(
                        new BigDecimal("32.735"), new BigDecimal("-97.108"), "abc123", 160934L, "7203.500s")));
        ManifestRouteServiceImpl service =
                new ManifestRouteServiceImpl(this.vektorManifestRepository, this.googleRoutesClient);

        Optional<ManifestRouteResponse> result = service.findRoute(1000589L);

        assertThat(result).isPresent();
        ManifestRouteResponse response = result.get();
        assertThat(response.originLatitude()).isEqualByComparingTo("32.735");
        assertThat(response.originLongitude()).isEqualByComparingTo("-97.108");
        assertThat(response.destinationLatitude()).isEqualByComparingTo("33.101");
        assertThat(response.destinationLongitude()).isEqualByComparingTo("-87.99");
        assertThat(response.encodedPolyline()).isEqualTo("abc123");
        assertThat(response.distanceMeters()).isEqualTo(160934L);
        assertThat(response.duration()).isEqualTo("7203.500s");
    }

    private VektorManifestRow manifestRow(BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        return new VektorManifestRow(
                1000589L,
                "71da0ba8-865b-4c1a-8ad1-b95a4d2b8398",
                "b4a58cf3-150c-4ab8-9f9a-31a03da29bc2",
                "Warren Ruawhare",
                "41000123",
                "manifest_in_progress",
                "4251 Turin Dr, Bessemer, AL 35020",
                "6390 N Alsup Rd, Litchfield Park, AZ 85340",
                destinationLatitude,
                destinationLongitude,
                LocalDateTime.of(2026, 7, 17, 8, 0),
                LocalDateTime.of(2026, 7, 20, 10, 0),
                "SwX-1000589",
                "{}",
                null);
    }
}
