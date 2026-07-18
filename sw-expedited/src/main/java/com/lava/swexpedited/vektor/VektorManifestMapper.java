package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded Vektor manifest ({@code Manifests/Get}'s repeated field 3) to a {@link VektorManifestRow}. Field
 * numbers below are reverse-engineered from real captured traffic, cross-validated end-to-end against a real
 * dispatch-sheet PDF (see the Vektor manifest sync plan) - there's no {@code .proto} schema to generate this from.
 *
 * <p>A manifest's stops (field 32, repeated) each carry a stop-type marker (field 22): {@code 1} for a pickup,
 * {@code 4} for a dropoff, confirmed against the PDF's "Pickup"/"Dropoff" labels. This app only models a single
 * origin/destination per manifest (Phase 1 scope) - the <em>first</em> pickup stop becomes {@code origin}, the
 * <em>last</em> dropoff stop becomes {@code destination}, so a multi-stop manifest still resolves to "where it started"
 * and "where it's ultimately headed" rather than an arbitrary middle stop. Each of those two stops also carries an
 * {@code appointment_start_datetime} (field 24), stored as {@code pickupAppointmentStart}/{@code eta} respectively -
 * this is a scheduled/appointment time, not an actual arrival/departure time (no such field has been observed).
 */
@Component
public class VektorManifestMapper {

    private static final int STOP_TYPE_PICKUP = 1;
    private static final int STOP_TYPE_DROPOFF = 4;
    private static final DateTimeFormatter APPOINTMENT_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public VektorManifestRow toRow(VektorGrpcWeb.Message manifest, Map<String, String> driverNamesById) {
        String manifestId = manifest.getString(1).orElseThrow();
        long manifestNumber = manifest.getVarint(2)
                .orElseThrow(() ->
                        new IllegalStateException("manifest " + manifestId + " has no manifest number (field 2)"));
        String driverId =
                manifest.getString(35).or(() -> manifest.getString(36)).orElse(null);
        String driverName = driverId == null ? null : driverNamesById.get(driverId);
        String status = manifest.getString(50).orElse(null);

        List<VektorGrpcWeb.Message> stops = manifest.getMessages(32);
        VektorGrpcWeb.Message pickup = firstStopOfType(stops, STOP_TYPE_PICKUP);
        VektorGrpcWeb.Message dropoff = lastStopOfType(stops, STOP_TYPE_DROPOFF);

        return new VektorManifestRow(
                manifestNumber,
                manifestId,
                driverId,
                driverName,
                null,
                status,
                pickup == null ? null : formattedAddress(pickup),
                dropoff == null ? null : formattedAddress(dropoff),
                dropoff == null ? null : latitude(dropoff),
                dropoff == null ? null : longitude(dropoff),
                pickup == null ? null : parseAppointmentStart(pickup),
                dropoff == null ? null : parseAppointmentStart(dropoff),
                loadReference(pickup, dropoff),
                writeManifestAsJson(manifest),
                null);
    }

    private VektorGrpcWeb.@Nullable Message firstStopOfType(List<VektorGrpcWeb.Message> stops, int stopType) {
        return stops.stream()
                .filter(stop -> isStopType(stop, stopType))
                .findFirst()
                .orElse(null);
    }

    private VektorGrpcWeb.@Nullable Message lastStopOfType(List<VektorGrpcWeb.Message> stops, int stopType) {
        return stops.stream()
                .filter(stop -> isStopType(stop, stopType))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private boolean isStopType(VektorGrpcWeb.Message stop, int stopType) {
        return stop.getVarint(22).map(type -> type == stopType).orElse(false);
    }

    private @Nullable String formattedAddress(VektorGrpcWeb.Message stop) {
        return stop.getMessage(19).flatMap(location -> location.getString(4)).orElse(null);
    }

    private @Nullable BigDecimal latitude(VektorGrpcWeb.Message stop) {
        return stop.getMessage(19)
                .flatMap(location -> location.getDouble(2))
                .map(BigDecimal::valueOf)
                .orElse(null);
    }

    private @Nullable BigDecimal longitude(VektorGrpcWeb.Message stop) {
        return stop.getMessage(19)
                .flatMap(location -> location.getDouble(3))
                .map(BigDecimal::valueOf)
                .orElse(null);
    }

    private @Nullable LocalDateTime parseAppointmentStart(VektorGrpcWeb.Message stop) {
        return stop.getString(24)
                .map(text -> LocalDateTime.parse(text, APPOINTMENT_DATE_TIME_FORMAT))
                .orElse(null);
    }

    private @Nullable String loadReference(
            VektorGrpcWeb.@Nullable Message pickup, VektorGrpcWeb.@Nullable Message dropoff) {
        if (dropoff != null) {
            return dropoff.getString(4).orElse(null);
        }
        return pickup == null ? null : pickup.getString(4).orElse(null);
    }

    private String writeManifestAsJson(VektorGrpcWeb.Message manifest) {
        try {
            return objectMapper.writeValueAsString(manifest.toGenericValue());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize a Vektor manifest for raw_response", e);
        }
    }
}
