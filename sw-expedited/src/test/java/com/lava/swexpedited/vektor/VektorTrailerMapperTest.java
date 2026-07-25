package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VektorTrailerMapperTest {

    private final VektorTrailerMapper vektorTrailerMapper = new VektorTrailerMapper();

    @Test
    void toRow_fullTrailer_mapsAllConfirmedFields() {
        VektorGrpcWeb.Message trailer = decode(new VektorGrpcWeb.Writer()
                .writeString(1, "trailer-uuid")
                .writeString(4, "T231 - 53' SDL")
                .writeString(7, "FONTAINE TRAILER CO.")
                .writeVarint(9, 2019));

        VektorTrailerRow row = vektorTrailerMapper.toRow(trailer);

        assertThat(row.id()).isEqualTo("trailer-uuid");
        assertThat(row.label()).isEqualTo("T231 - 53' SDL");
        assertThat(row.manufacturer()).isEqualTo("FONTAINE TRAILER CO.");
        assertThat(row.year()).isEqualTo(2019);
        assertThat(row.rawResponse()).isNotBlank().contains("FONTAINE");
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_labelWithoutDashSuffix_labelStoredAsIs() {
        VektorGrpcWeb.Message trailer =
                decode(new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid").writeString(4, "U51620"));

        VektorTrailerRow row = vektorTrailerMapper.toRow(trailer);

        assertThat(row.label()).isEqualTo("U51620");
    }

    @Test
    void toRow_noId_throws() {
        VektorGrpcWeb.Message trailer = decode(new VektorGrpcWeb.Writer().writeString(4, "U51620"));

        assertThatThrownBy(() -> vektorTrailerMapper.toRow(trailer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }

    @Test
    void toRow_rawResponseIncludesUnmappedFields() {
        VektorGrpcWeb.Message trailer =
                decode(new VektorGrpcWeb.Writer().writeString(1, "trailer-uuid").writeVarint(5, 1));

        VektorTrailerRow row = vektorTrailerMapper.toRow(trailer);

        assertThat(row.rawResponse()).contains("\"5\"");
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
