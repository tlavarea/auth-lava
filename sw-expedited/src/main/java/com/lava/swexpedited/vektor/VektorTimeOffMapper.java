package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded {@code TruckTimeOff/Get} entry (already flattened out of its truck-group/wrapper nesting by
 * {@link com.lava.swexpedited.batch.vektor.VektorTimeOffClient}) to a {@link VektorTimeOffRow}. Field numbers
 * reverse-engineered from a real captured response (see the Vektor manifest sync plan): {@code 1} truck_id (repeated at
 * both the truck-group and entry level; read here from the entry itself), {@code 2} the entry's own stable id (used as
 * the upsert key), {@code 3}/{@code 4} start/end datetime in the same {@code yyyy-MM-dd HH:mm:ss} format as
 * {@code VektorManifestMapper}'s appointment timestamps, {@code 5} a free-text reason. Fields {@code 6}/{@code 7} were
 * two constant UUIDs across every entry in the one capture available (purpose unconfirmed - plausibly a type/category
 * id and a company id) and aren't surfaced as columns, only in {@code raw_response}.
 */
@Component
public class VektorTimeOffMapper {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VektorTimeOffRow toRow(VektorGrpcWeb.Message entry) {
        String truckId = entry.getString(1)
                .orElseThrow(() -> new IllegalStateException("time-off entry has no " + "truck_id (field 1)"));
        String id =
                entry.getString(2).orElseThrow(() -> new IllegalStateException("time-off entry has no id (field 2)"));
        LocalDateTime startAt = entry.getString(3)
                .map(this::parseDateTime)
                .orElseThrow(() -> new IllegalStateException("time-off entry " + id + " has no start (field 3)"));
        LocalDateTime endAt = entry.getString(4)
                .map(this::parseDateTime)
                .orElseThrow(() -> new IllegalStateException("time-off entry " + id + " has no end (field 4)"));
        String reason = entry.getString(5).orElse(null);

        return new VektorTimeOffRow(id, truckId, null, startAt, endAt, reason, writeEntryAsJson(entry), null);
    }

    private LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(text, DATE_TIME_FORMAT);
    }

    private String writeEntryAsJson(VektorGrpcWeb.Message entry) {
        try {
            return objectMapper.writeValueAsString(entry.toGenericValue());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Vektor time-off entry for raw_response", e);
        }
    }
}
