package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VektorGrpcWebTest {

    @Test
    void encodeThenDecode_scalarFields_roundTrips() {
        VektorGrpcWeb.Writer writer = new VektorGrpcWeb.Writer()
                .writeString(1, "tlavarea@gmail.com")
                .writeVarint(2, 1000589L)
                .writeString(50, "manifest_in_progress");

        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));

        assertThat(message.getString(1)).contains("tlavarea@gmail.com");
        assertThat(message.getVarint(2)).contains(1000589L);
        assertThat(message.getString(50)).contains("manifest_in_progress");
    }

    @Test
    void encodeThenDecode_nestedMessage_roundTrips() {
        VektorGrpcWeb.Writer location = new VektorGrpcWeb.Writer()
                .writeString(4, "4251 Turin Dr, Bessemer, AL 35020")
                .writeDouble(2, 33.3937585d);
        VektorGrpcWeb.Writer stop =
                new VektorGrpcWeb.Writer().writeVarint(22, 1).writeMessage(19, location);
        VektorGrpcWeb.Writer manifest = new VektorGrpcWeb.Writer().writeMessage(32, stop);

        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(manifest));

        VektorGrpcWeb.Message decodedStop = message.getMessage(32).orElseThrow();
        assertThat(decodedStop.getVarint(22)).contains(1L);
        VektorGrpcWeb.Message decodedLocation = decodedStop.getMessage(19).orElseThrow();
        assertThat(decodedLocation.getString(4)).contains("4251 Turin Dr, Bessemer, AL 35020");
        assertThat(decodedLocation.getDouble(2)).contains(33.3937585d);
    }

    @Test
    void encodeThenDecode_repeatedMessages_returnsAllInOrder() {
        VektorGrpcWeb.Writer manifest = new VektorGrpcWeb.Writer()
                .writeVarint(2, 1000586L)
                .writeMessage(3, new VektorGrpcWeb.Writer().writeVarint(2, 1000587L))
                .writeMessage(3, new VektorGrpcWeb.Writer().writeVarint(2, 1000588L))
                .writeMessage(3, new VektorGrpcWeb.Writer().writeVarint(2, 1000589L));

        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(manifest));

        List<VektorGrpcWeb.Message> manifests = message.getMessages(3);
        assertThat(manifests)
                .extracting(m -> m.getVarint(2).orElseThrow())
                .containsExactly(1000587L, 1000588L, 1000589L);
    }

    @Test
    void encodeThenDecode_repeatedStrings_returnsAllValues() {
        VektorGrpcWeb.Writer statuses = new VektorGrpcWeb.Writer()
                .writeString(1, "manifest_planning")
                .writeString(1, "manifest_dispatched")
                .writeString(1, "manifest_in_progress");

        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(statuses));

        assertThat(message.getStrings(1))
                .containsExactly("manifest_planning", "manifest_dispatched", "manifest_in_progress");
    }

    @Test
    void decodeUnaryResponse_nonZeroGrpcStatus_throws() {
        byte[] dataFrame = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeString(1, "unused"));
        byte[] trailerPayload = "grpc-status:16\r\ngrpc-message:Authorization header is not provided.\r\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] trailerFrame = new byte[5 + trailerPayload.length];
        trailerFrame[0] = (byte) 0x80;
        trailerFrame[4] = (byte) trailerPayload.length;
        System.arraycopy(trailerPayload, 0, trailerFrame, 5, trailerPayload.length);
        byte[] response = new byte[dataFrame.length + trailerFrame.length];
        System.arraycopy(dataFrame, 0, response, 0, dataFrame.length);
        System.arraycopy(trailerFrame, 0, response, dataFrame.length, trailerFrame.length);

        assertThatThrownBy(() -> VektorGrpcWeb.decodeUnaryResponse(response))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class)
                .hasMessageContaining("16");
    }

    @Test
    void decodeUnaryResponse_missingTrailerFrame_throws() {
        byte[] response = VektorGrpcWeb.encodeUnaryRequest(new VektorGrpcWeb.Writer().writeString(1, "unused"));

        assertThatThrownBy(() -> VektorGrpcWeb.decodeUnaryResponse(response))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class);
    }

    @Test
    void toGenericValue_stringAndNestedMessage_bothDecodeReadably() {
        VektorGrpcWeb.Writer nested = new VektorGrpcWeb.Writer().writeString(1, "Bessemer");
        VektorGrpcWeb.Writer top = new VektorGrpcWeb.Writer()
                .writeString(1, "manifest_in_progress")
                .writeMessage(2, nested);

        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(top));

        @SuppressWarnings("unchecked")
        Map<String, Object> generic = (Map<String, Object>) message.toGenericValue();
        assertThat(generic.get("1")).isEqualTo("manifest_in_progress");
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedGeneric = (Map<String, Object>) generic.get("2");
        assertThat(nestedGeneric.get("1")).isEqualTo("Bessemer");
    }

    @Test
    void getString_missingField_isEmpty() {
        VektorGrpcWeb.Message message = VektorGrpcWeb.decodeUnaryResponse(
                VektorGrpcWeb.encodeUnaryResponse(new VektorGrpcWeb.Writer().writeVarint(1, 1)));

        assertThat(message.getString(99)).isEqualTo(Optional.empty());
    }
}
