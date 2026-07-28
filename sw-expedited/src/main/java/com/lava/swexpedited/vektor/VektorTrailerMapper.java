package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded {@code Trailers/Get} entry to a {@link VektorTrailerRow}. Field numbers reverse-engineered from a
 * real captured response: {@code 1} trailer_id (matches {@code vektor_truck.current_trailer_id}), {@code 4} a combined
 * display label (e.g. {@code "T231 - 53' SDL"} - stored as-is, not split, since not every trailer's label has the
 * {@code " - "} separator), {@code 6} vin, {@code 7} manufacturer, {@code 9} year. Other captured-but-unconfirmed
 * fields (status/type/ownership enums, one unexplained numeric field) aren't surfaced as columns, only in
 * {@code raw_response}.
 */
@Component
public class VektorTrailerMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VektorTrailerRow toRow(VektorGrpcWeb.Message trailer) {
        String id = trailer.getString(1).orElseThrow(() -> new IllegalStateException("trailer has no id (field 1)"));
        String label = trailer.getString(4).orElse(null);
        String vin = trailer.getString(6).orElse(null);
        String manufacturer = trailer.getString(7).orElse(null);
        Integer year = trailer.getVarint(9).map(Long::intValue).orElse(null);

        return new VektorTrailerRow(id, label, manufacturer, year, vin, writeAsJson(trailer), null, null);
    }

    private String writeAsJson(VektorGrpcWeb.Message trailer) {
        try {
            return objectMapper.writeValueAsString(trailer.toGenericValue());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Vektor trailer entry for raw_response", e);
        }
    }
}
