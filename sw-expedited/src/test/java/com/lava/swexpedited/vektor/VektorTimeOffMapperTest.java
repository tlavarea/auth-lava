package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VektorTimeOffMapperTest {

    private final VektorTimeOffMapper vektorTimeOffMapper = new VektorTimeOffMapper();

    @Test
    void toRow_fullEntry_mapsAllFields() {
        VektorGrpcWeb.Message entry = decode(entryWriter());

        VektorTimeOffRow row = vektorTimeOffMapper.toRow(entry);

        assertThat(row.id()).isEqualTo("time-off-uuid");
        assertThat(row.truckId()).isEqualTo("truck-uuid");
        assertThat(row.startAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 0, 0, 0));
        assertThat(row.endAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 0, 0, 0));
        assertThat(row.reason()).isEqualTo("Vacation");
        assertThat(row.rawResponse()).isNotBlank().contains("Vacation");
        assertThat(row.matchedSamsaraDriverId()).isNull();
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_noReason_reasonIsNull() {
        VektorGrpcWeb.Writer entryWithoutReason = new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(2, "time-off-uuid")
                .writeString(3, "2026-07-17 00:00:00")
                .writeString(4, "2026-07-20 00:00:00");

        VektorTimeOffRow row = vektorTimeOffMapper.toRow(decode(entryWithoutReason));

        assertThat(row.reason()).isNull();
    }

    @Test
    void toRow_noTruckId_throws() {
        VektorGrpcWeb.Writer entryWithoutTruckId = new VektorGrpcWeb.Writer()
                .writeString(2, "time-off-uuid")
                .writeString(3, "2026-07-17 00:00:00")
                .writeString(4, "2026-07-20 00:00:00");

        assertThatThrownBy(() -> vektorTimeOffMapper.toRow(decode(entryWithoutTruckId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("truck_id");
    }

    @Test
    void toRow_noId_throws() {
        VektorGrpcWeb.Writer entryWithoutId = new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(3, "2026-07-17 00:00:00")
                .writeString(4, "2026-07-20 00:00:00");

        assertThatThrownBy(() -> vektorTimeOffMapper.toRow(decode(entryWithoutId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }

    @Test
    void toRow_noStart_throws() {
        VektorGrpcWeb.Writer entryWithoutStart = new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(2, "time-off-uuid")
                .writeString(4, "2026-07-20 00:00:00");

        assertThatThrownBy(() -> vektorTimeOffMapper.toRow(decode(entryWithoutStart)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start");
    }

    @Test
    void toRow_noEnd_throws() {
        VektorGrpcWeb.Writer entryWithoutEnd = new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(2, "time-off-uuid")
                .writeString(3, "2026-07-17 00:00:00");

        assertThatThrownBy(() -> vektorTimeOffMapper.toRow(decode(entryWithoutEnd)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("end");
    }

    private VektorGrpcWeb.Writer entryWriter() {
        return new VektorGrpcWeb.Writer()
                .writeString(1, "truck-uuid")
                .writeString(2, "time-off-uuid")
                .writeString(3, "2026-07-17 00:00:00")
                .writeString(4, "2026-07-20 00:00:00")
                .writeString(5, "Vacation");
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
