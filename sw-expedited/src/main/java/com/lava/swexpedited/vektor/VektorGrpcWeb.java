package com.lava.swexpedited.vektor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-rolled Protocol Buffers wire-format encoder/decoder plus gRPC-Web framing, purpose-built for app.vektortms.com's
 * internal gRPC-Web backend. Vektor has no public API, no published {@code .proto} schema, and {@code protoc} isn't
 * available in this build - every field number/type this class's callers rely on was reverse-engineered by decoding
 * real captured browser traffic byte-by-byte (see the Vektor manifest sync plan for that investigation), not generated
 * from a schema.
 *
 * <p><b>gRPC-Web framing</b> (the binary {@code application/grpc-web+proto} variant, not the base64 "-text" one): the
 * HTTP body is one or more length-prefixed frames, each a 1-byte flag (0x00 = a protobuf-encoded message, 0x80 = the
 * trailer - HTTP/1.1 has no real trailers, so gRPC-Web smuggles them as one more frame) followed by a 4-byte
 * <em>big-endian</em> length and that many bytes of payload. A successful trailer frame's payload is the plain text
 * {@code grpc-status:0\r\ngrpc-message:\r\n}. Every Vektor RPC this app calls is unary (one request message, one
 * response message, confirmed against real captures) - {@link #decodeUnaryResponse} expects exactly one data frame.
 *
 * <p><b>Protobuf wire format</b> has no field names on the wire, only a (field number, wire type) tag per field - wire
 * type 0 (varint: int32/int64/uint64/bool/enum), 1 (fixed64: raw 8 bytes, <em>little-endian</em> - note this is the
 * opposite byte order from the frame length above), 2 (length-delimited: strings, bytes, and nested messages all share
 * this wire type - there's no way to tell them apart without knowing the field's real type from other evidence), or 5
 * (fixed32, little-endian). {@link Message} exposes typed accessors over that raw structure; callers pick the accessor
 * matching what real captured data showed a given field number actually holds.
 */
public final class VektorGrpcWeb {

    private static final byte DATA_FRAME_FLAG = 0x00;
    private static final byte TRAILER_FRAME_FLAG = (byte) 0x80;

    private VektorGrpcWeb() {}

    /** Wraps one protobuf-encoded request message in a single gRPC-Web data frame. */
    public static byte[] encodeUnaryRequest(Writer writer) {
        return frame(DATA_FRAME_FLAG, writer.toByteArray());
    }

    /**
     * Builds a synthetic gRPC-Web unary response - a data frame plus a successful trailer frame - the same shape
     * {@link #decodeUnaryResponse} expects. Production code never calls this (it only ever decodes Vektor's real
     * responses), but tests stubbing a Vektor endpoint (WireMock, etc.) need to build a realistic response body, and
     * building it with this rather than duplicating the frame format in test code keeps one source of truth for it.
     */
    public static byte[] encodeUnaryResponse(Writer writer) {
        byte[] dataFrame = frame(DATA_FRAME_FLAG, writer.toByteArray());
        byte[] trailerFrame =
                frame(TRAILER_FRAME_FLAG, "grpc-status:0\r\ngrpc-message:\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] response = new byte[dataFrame.length + trailerFrame.length];
        System.arraycopy(dataFrame, 0, response, 0, dataFrame.length);
        System.arraycopy(trailerFrame, 0, response, dataFrame.length, trailerFrame.length);
        return response;
    }

    private static byte[] frame(byte flag, byte[] payload) {
        byte[] framed = new byte[5 + payload.length];
        framed[0] = flag;
        framed[1] = (byte) (payload.length >>> 24);
        framed[2] = (byte) (payload.length >>> 16);
        framed[3] = (byte) (payload.length >>> 8);
        framed[4] = (byte) payload.length;
        System.arraycopy(payload, 0, framed, 5, payload.length);
        return framed;
    }

    /**
     * Parses a gRPC-Web unary response body: splits it into frames, verifies the trailer frame reports
     * {@code grpc-status:0}, and returns the single data frame's message.
     */
    public static Message decodeUnaryResponse(byte[] responseBody) {
        List<Message> dataFrames = new ArrayList<>();
        String grpcStatus = null;
        int i = 0;
        while (i < responseBody.length) {
            byte flag = responseBody[i];
            long length = readUint32BigEndian(responseBody, i + 1);
            int payloadStart = i + 5;
            int payloadEnd = (int) (payloadStart + length);
            byte[] payload = Arrays.copyOfRange(responseBody, payloadStart, payloadEnd);
            if (flag == TRAILER_FRAME_FLAG) {
                grpcStatus = parseTrailerStatus(payload);
            } else {
                dataFrames.add(new Message(parseFields(payload)));
            }
            i = payloadEnd;
        }
        if (!"0".equals(grpcStatus)) {
            throw new VektorGrpcWebException("gRPC-Web response missing a successful (grpc-status:0) trailer, got: "
                    + (grpcStatus == null ? "(no trailer frame)" : grpcStatus));
        }
        if (dataFrames.size() != 1) {
            throw new VektorGrpcWebException(
                    "expected exactly one data frame in a unary gRPC-Web response, got " + dataFrames.size());
        }
        return dataFrames.getFirst();
    }

    private static String parseTrailerStatus(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        for (String line : text.split("\r\n")) {
            if (line.startsWith("grpc-status:")) {
                return line.substring("grpc-status:".length()).trim();
            }
        }
        return null;
    }

    private static long readUint32BigEndian(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static Map<Integer, List<Object>> parseFields(byte[] data) {
        Map<Integer, List<Object>> fields = new LinkedHashMap<>();
        int i = 0;
        while (i < data.length) {
            VarintResult tag = readVarint(data, i);
            int fieldNumber = (int) (tag.value() >>> 3);
            int wireType = (int) (tag.value() & 0x7);
            Object value;
            int nextIndex;
            switch (wireType) {
                case 0 -> {
                    VarintResult varint = readVarint(data, tag.nextIndex());
                    value = varint.value();
                    nextIndex = varint.nextIndex();
                }
                case 1 -> {
                    // Every wire-type-1 field observed in real Vektor traffic is a latitude/longitude double -
                    // decoded eagerly here rather than kept as raw bits, so getDouble/toGenericValue both see a
                    // plain Double with no separate "raw fixed64" accessor needed anywhere in this codebase.
                    value = Double.longBitsToDouble(readFixed64LittleEndian(data, tag.nextIndex()));
                    nextIndex = tag.nextIndex() + 8;
                }
                case 2 -> {
                    VarintResult len = readVarint(data, tag.nextIndex());
                    int start = len.nextIndex();
                    int end = start + (int) len.value();
                    value = Arrays.copyOfRange(data, start, end);
                    nextIndex = end;
                }
                case 5 -> {
                    value = readFixed32LittleEndian(data, tag.nextIndex());
                    nextIndex = tag.nextIndex() + 4;
                }
                default ->
                    throw new VektorGrpcWebException(
                            "unsupported protobuf wire type " + wireType + " for field " + fieldNumber);
            }
            fields.computeIfAbsent(fieldNumber, key -> new ArrayList<>()).add(value);
            i = nextIndex;
        }
        return fields;
    }

    private static VarintResult readVarint(byte[] data, int index) {
        long value = 0;
        int shift = 0;
        int i = index;
        while (true) {
            byte b = data[i++];
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return new VarintResult(value, i);
    }

    private static long readFixed64LittleEndian(byte[] data, int offset) {
        long value = 0;
        for (int b = 0; b < 8; b++) {
            value |= (long) (data[offset + b] & 0xFF) << (8 * b);
        }
        return value;
    }

    private static int readFixed32LittleEndian(byte[] data, int offset) {
        int value = 0;
        for (int b = 0; b < 4; b++) {
            value |= (data[offset + b] & 0xFF) << (8 * b);
        }
        return value;
    }

    private record VarintResult(long value, int nextIndex) {}

    /** Builds one protobuf message's bytes, field by field, in whatever order the caller writes them. */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public Writer writeVarint(int fieldNumber, long value) {
            writeTag(fieldNumber, 0);
            writeRawVarint(value);
            return this;
        }

        public Writer writeString(int fieldNumber, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeTag(fieldNumber, 2);
            writeRawVarint(bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        public Writer writeMessage(int fieldNumber, Writer nested) {
            byte[] bytes = nested.toByteArray();
            writeTag(fieldNumber, 2);
            writeRawVarint(bytes.length);
            out.writeBytes(bytes);
            return this;
        }

        /** Mirrors {@link Message#getDouble} - writes a wire-type-1 (fixed64) field as little-endian IEEE 754 bits. */
        public Writer writeDouble(int fieldNumber, double value) {
            writeTag(fieldNumber, 1);
            long bits = Double.doubleToLongBits(value);
            for (int i = 0; i < 8; i++) {
                out.write((int) (bits >>> (8 * i)));
            }
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }

        private void writeTag(int fieldNumber, int wireType) {
            writeRawVarint(((long) fieldNumber << 3) | wireType);
        }

        private void writeRawVarint(long value) {
            long remaining = value;
            while (true) {
                if ((remaining & ~0x7FL) == 0) {
                    out.write((int) remaining);
                    return;
                }
                out.write((int) ((remaining & 0x7F) | 0x80));
                remaining >>>= 7;
            }
        }
    }

    /**
     * A decoded protobuf message with no schema attached - just the raw (field number -&gt; values) structure typed
     * accessors interpret against. Multiple values under the same field number means a repeated field; wire type 2
     * (length-delimited) values are exposed both as strings ({@link #getString}/{@link #getStrings}) and as nested
     * messages ({@link #getMessage}/{@link #getMessages}), since the wire format alone can't distinguish a string field
     * from an embedded message field.
     */
    public static final class Message {
        private final Map<Integer, List<Object>> fields;

        private Message(Map<Integer, List<Object>> fields) {
            this.fields = fields;
        }

        public Optional<String> getString(int fieldNumber) {
            return getLengthDelimited(fieldNumber).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
        }

        public List<String> getStrings(int fieldNumber) {
            return valuesOf(fieldNumber).stream()
                    .filter(byte[].class::isInstance)
                    .map(value -> new String((byte[]) value, StandardCharsets.UTF_8))
                    .toList();
        }

        public Optional<Long> getVarint(int fieldNumber) {
            return valuesOf(fieldNumber).stream()
                    .filter(Long.class::isInstance)
                    .map(value -> (Long) value)
                    .findFirst();
        }

        /**
         * A wire-type-1 (fixed64) field, decoded as a double - see the parser's note on why that's always safe here.
         */
        public Optional<Double> getDouble(int fieldNumber) {
            return valuesOf(fieldNumber).stream()
                    .filter(Double.class::isInstance)
                    .map(value -> (Double) value)
                    .findFirst();
        }

        public Optional<Message> getMessage(int fieldNumber) {
            return getLengthDelimited(fieldNumber).map(bytes -> new Message(parseFields(bytes)));
        }

        public List<Message> getMessages(int fieldNumber) {
            return valuesOf(fieldNumber).stream()
                    .filter(byte[].class::isInstance)
                    .map(value -> new Message(parseFields((byte[]) value)))
                    .toList();
        }

        /**
         * Dumps this message's full raw structure (field number -&gt; value(s), recursing into nested messages) as
         * plain {@link Map}/{@link List}/{@link String}/{@link Long}/{@link Double} values, ready for
         * {@code ObjectMapper#writeValueAsString} - used only to populate {@code vektor_manifest.raw_response}, not by
         * any typed accessor. Length-delimited fields are ambiguous on the wire (string vs. nested message vs. opaque
         * bytes), so this guesses by trying UTF-8 text first (rejecting anything with non-printable, non- whitespace
         * control characters) and falling back to a nested message otherwise.
         */
        public Object toGenericValue() {
            Map<String, Object> result = new LinkedHashMap<>();
            fields.forEach((fieldNumber, values) -> {
                List<Object> decoded =
                        values.stream().map(Message::decodeGenericValue).toList();
                result.put(String.valueOf(fieldNumber), decoded.size() == 1 ? decoded.getFirst() : decoded);
            });
            return result;
        }

        private static Object decodeGenericValue(Object raw) {
            if (!(raw instanceof byte[] bytes)) {
                return raw;
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            boolean looksLikeText =
                    text.chars().allMatch(c -> c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0x7F));
            return looksLikeText ? text : new Message(parseFields(bytes)).toGenericValue();
        }

        private Optional<byte[]> getLengthDelimited(int fieldNumber) {
            return valuesOf(fieldNumber).stream()
                    .filter(byte[].class::isInstance)
                    .map(value -> (byte[]) value)
                    .findFirst();
        }

        private List<Object> valuesOf(int fieldNumber) {
            return fields.getOrDefault(fieldNumber, List.of());
        }
    }

    public static class VektorGrpcWebException extends RuntimeException {
        public VektorGrpcWebException(String message) {
            super(message);
        }
    }
}
