package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VektorTruckMapperTest {

    private final VektorTruckMapper vektorTruckMapper = new VektorTruckMapper();

    @Test
    void toRow_fullTruck_mapsAllConfirmedFields() {
        VektorGrpcWeb.Message truck = decode(new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(2, "2401")
                .writeVarint(3, 1)
                .writeString(4, "1XKYD49X5KJ284936")
                .writeString(5, "KENWORTH")
                .writeString(6, "T680")
                .writeVarint(7, 2019)
                .writeString(8, "blue")
                .writeString(16, "trailer-uuid")
                .writeMessage(
                        17,
                        new VektorGrpcWeb.Writer().writeString(1, "driver-uuid").writeVarint(2, 1)));

        VektorTruckRow row = vektorTruckMapper.toRow(truck);

        assertThat(row.id()).isEqualTo("truck-uuid");
        assertThat(row.truckNumber()).isEqualTo("2401");
        assertThat(row.statusCode()).isEqualTo(1);
        assertThat(row.vin()).isEqualTo("1XKYD49X5KJ284936");
        assertThat(row.make()).isEqualTo("KENWORTH");
        assertThat(row.model()).isEqualTo("T680");
        assertThat(row.year()).isEqualTo(2019);
        assertThat(row.currentTrailerId()).isEqualTo("trailer-uuid");
        assertThat(row.currentDriverId()).isEqualTo("driver-uuid");
        assertThat(row.rawResponse()).isNotBlank().contains("blue");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_noCurrentTrailerAssignment_currentTrailerIdIsNull() {
        VektorGrpcWeb.Message truck = decode(new VektorGrpcWeb.Writer().writeString(1, "truck-uuid"));

        VektorTruckRow row = vektorTruckMapper.toRow(truck);

        assertThat(row.currentTrailerId()).isNull();
    }

    @Test
    void toRow_noCurrentDriverAssignment_currentDriverIdIsNull() {
        VektorGrpcWeb.Message truck = decode(new VektorGrpcWeb.Writer().writeString(1, "truck-uuid"));

        VektorTruckRow row = vektorTruckMapper.toRow(truck);

        assertThat(row.currentDriverId()).isNull();
    }

    @Test
    void toRow_noId_throws() {
        VektorGrpcWeb.Message truck = decode(new VektorGrpcWeb.Writer().writeString(2, "2401"));

        assertThatThrownBy(() -> vektorTruckMapper.toRow(truck))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }

    @Test
    void toRow_rawResponseIncludesUnmappedFields() {
        VektorGrpcWeb.Message truck =
                decode(new VektorGrpcWeb.Writer().writeString(1, "truck-uuid").writeString(8, "maroon"));

        VektorTruckRow row = vektorTruckMapper.toRow(truck);

        assertThat(row.rawResponse()).contains("maroon");
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
