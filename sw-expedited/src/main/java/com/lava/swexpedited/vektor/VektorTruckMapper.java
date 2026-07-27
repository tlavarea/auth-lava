package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded {@code Trucks/Get} entry to a {@link VektorTruckRow}. Field numbers reverse-engineered from a real
 * captured response: {@code 1} truck_id, {@code 2} truck_number, {@code 3} status_code (a raw enum-like integer whose
 * exact meaning isn't fully confirmed - see the 016 migration's changelog comment), {@code 4} VIN, {@code 5} make,
 * {@code 6} model, {@code 7} year, {@code 16} current_trailer_id, {@code 17} a nested message whose own field {@code 1}
 * is the current primary driver assignment's driver_id (confirmed by cross-referencing against {@code Drivers/Get}'s
 * id-&gt;name map and this app's real Schedule page driver names). Other captured-but- unconfirmed fields (color, toll
 * transponder number, weights, registration country/state) aren't surfaced as columns, only in {@code raw_response}.
 */
@Component
public class VektorTruckMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VektorTruckRow toRow(VektorGrpcWeb.Message truck) {
        String id = truck.getString(1).orElseThrow(() -> new IllegalStateException("truck has no id (field 1)"));
        String truckNumber = truck.getString(2).orElse(null);
        Integer statusCode = truck.getVarint(3).map(Long::intValue).orElse(null);
        String vin = truck.getString(4).orElse(null);
        String make = truck.getString(5).orElse(null);
        String model = truck.getString(6).orElse(null);
        Integer year = truck.getVarint(7).map(Long::intValue).orElse(null);
        String currentTrailerId = truck.getString(16).orElse(null);
        String currentDriverId = truck.getMessage(17)
                .flatMap(assignment -> assignment.getString(1))
                .orElse(null);

        return new VektorTruckRow(
                id,
                truckNumber,
                statusCode,
                vin,
                make,
                model,
                year,
                currentTrailerId,
                currentDriverId,
                writeAsJson(truck),
                null,
                null);
    }

    private String writeAsJson(VektorGrpcWeb.Message truck) {
        try {
            return objectMapper.writeValueAsString(truck.toGenericValue());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Vektor truck entry for raw_response", e);
        }
    }
}
