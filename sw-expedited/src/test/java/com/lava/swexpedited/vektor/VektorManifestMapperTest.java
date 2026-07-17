package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VektorManifestMapperTest {

    private static final String DRIVER_ID = "b4a58cf3-150c-4ab8-9f9a-31a03da29bc2";

    private final VektorManifestMapper vektorManifestMapper = new VektorManifestMapper();

    @Test
    void toRow_pickupAndDropoffStops_mapsOriginDestinationEtaAndDriver() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of(DRIVER_ID, "Warren Ruawhare"));

        assertThat(row.manifestNumber()).isEqualTo(1000589L);
        assertThat(row.manifestId()).isEqualTo("71da0ba8-865b-4c1a-8ad1-b95a4d2b8398");
        assertThat(row.driverId()).isEqualTo(DRIVER_ID);
        assertThat(row.driverName()).isEqualTo("Warren Ruawhare");
        assertThat(row.status()).isEqualTo("manifest_in_progress");
        assertThat(row.origin()).isEqualTo("4251 Turin Dr, Bessemer, AL 35020");
        assertThat(row.destination()).isEqualTo("6390 N Alsup Rd, Litchfield Park, AZ 85340");
        assertThat(row.destinationLatitude()).isEqualByComparingTo(BigDecimal.valueOf(33.5283256d));
        assertThat(row.destinationLongitude()).isEqualByComparingTo(BigDecimal.valueOf(-112.4031521d));
        assertThat(row.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0, 0));
        assertThat(row.loadReference()).isEqualTo("SwX-1000589");
        assertThat(row.rawResponse()).isNotBlank().contains("manifest_in_progress");
        assertThat(row.matchedSamsaraDriverId()).isNull();
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_driverIdNotInRoster_driverNameIsNull() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of("some-other-uuid", "Someone Else"));

        assertThat(row.driverId()).isEqualTo(DRIVER_ID);
        assertThat(row.driverName()).isNull();
    }

    @Test
    void toRow_multipleDropoffStops_usesLastAsDestination() {
        VektorGrpcWeb.Writer firstDropoff = new VektorGrpcWeb.Writer()
                .writeVarint(22, 4)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 34.0d)
                                .writeDouble(3, -111.0d)
                                .writeString(4, "Intermediate Stop, AZ"))
                .writeString(24, "2026-07-18 10:00:00");

        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), firstDropoff, dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.destination()).isEqualTo("6390 N Alsup Rd, Litchfield Park, AZ 85340");
        assertThat(row.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0, 0));
    }

    @Test
    void toRow_noPickupStop_originIsNull() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.origin()).isNull();
        assertThat(row.destination()).isNotNull();
    }

    private VektorGrpcWeb.Writer manifestWriter(VektorGrpcWeb.Writer... stops) {
        VektorGrpcWeb.Writer manifest = new VektorGrpcWeb.Writer()
                .writeString(1, "71da0ba8-865b-4c1a-8ad1-b95a4d2b8398")
                .writeVarint(2, 1000589L);
        for (VektorGrpcWeb.Writer stop : stops) {
            manifest.writeMessage(32, stop);
        }
        return manifest.writeString(35, DRIVER_ID).writeString(50, "manifest_in_progress");
    }

    private VektorGrpcWeb.Writer pickupStop() {
        return new VektorGrpcWeb.Writer()
                .writeVarint(22, 1)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 33.3937585d)
                                .writeDouble(3, -86.9302783d)
                                .writeString(4, "4251 Turin Dr, Bessemer, AL 35020"))
                .writeString(24, "2026-07-17 08:00:00");
    }

    private VektorGrpcWeb.Writer dropoffStop() {
        return new VektorGrpcWeb.Writer()
                .writeVarint(22, 4)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 33.5283256d)
                                .writeDouble(3, -112.4031521d)
                                .writeString(4, "6390 N Alsup Rd, Litchfield Park, AZ 85340"))
                .writeString(24, "2026-07-20 10:00:00");
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
