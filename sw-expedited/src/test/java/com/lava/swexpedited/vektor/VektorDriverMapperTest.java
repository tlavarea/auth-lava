package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VektorDriverMapperTest {

    private final VektorDriverMapper vektorDriverMapper = new VektorDriverMapper();

    @Test
    void toRow_fullDriver_mapsConfirmedFields() {
        VektorGrpcWeb.Message driver = decode(new VektorGrpcWeb.Writer()
                .writeString(1, "driver-uuid")
                .writeString(2, "8325")
                .writeString(7, "test.driver@example.com")
                .writeString(8, "+15550100")
                .writeString(35, "Test Driver"));

        VektorDriverRow row = vektorDriverMapper.toRow(driver);

        assertThat(row.id()).isEqualTo("driver-uuid");
        assertThat(row.driverNumber()).isEqualTo("8325");
        assertThat(row.email()).isEqualTo("test.driver@example.com");
        assertThat(row.phone()).isEqualTo("+15550100");
        assertThat(row.fullName()).isEqualTo("Test Driver");
        assertThat(row.rawResponse()).isNotBlank().contains("Test Driver");
        assertThat(row.matchedSamsaraDriverId()).isNull();
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_noDriverNumber_driverNumberIsNull() {
        VektorGrpcWeb.Message driver =
                decode(new VektorGrpcWeb.Writer().writeString(1, "driver-uuid").writeString(35, "Test Driver"));

        VektorDriverRow row = vektorDriverMapper.toRow(driver);

        assertThat(row.driverNumber()).isNull();
    }

    @Test
    void toRow_noFullName_fullNameIsNull() {
        VektorGrpcWeb.Message driver = decode(new VektorGrpcWeb.Writer().writeString(1, "driver-uuid"));

        VektorDriverRow row = vektorDriverMapper.toRow(driver);

        assertThat(row.fullName()).isNull();
    }

    @Test
    void toRow_noId_throws() {
        VektorGrpcWeb.Message driver = decode(new VektorGrpcWeb.Writer().writeString(35, "Test Driver"));

        assertThatThrownBy(() -> vektorDriverMapper.toRow(driver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }

    @Test
    void toRow_matchedSamsaraDriverIdStartsNull() {
        VektorGrpcWeb.Message driver = decode(new VektorGrpcWeb.Writer().writeString(1, "driver-uuid"));

        VektorDriverRow row = vektorDriverMapper.toRow(driver);

        assertThat(row.matchedSamsaraDriverId()).isNull();
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
