package com.lava.swexpedited.vektor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Maps one decoded Vektor manifest ({@code Manifests/Get}'s repeated field 3) to a {@link VektorManifestRow}. Field
 * numbers below are reverse-engineered from real captured traffic, cross-validated end-to-end against a real
 * dispatch-sheet PDF (see the Vektor manifest sync plan) - there's no {@code .proto} schema to generate this from.
 *
 * <p>A manifest's stops (field 32, repeated) each carry an entry-kind marker (field 1): {@code 1} for a real stop,
 * {@code 2} for a synthetic "starting position" entry (the truck's position when the manifest begins, carried over from
 * wherever its previous manifest left off - not a pickup/dropoff on this manifest). Real stops carry a stop-type marker
 * (field 22): {@code 1} for a pickup, {@code 4} for a dropoff, confirmed against the PDF's "Pickup"/"Dropoff" labels.
 *
 * <p>{@code origin}/{@code destination}/{@code destinationLatitude}/{@code destinationLongitude}/
 * {@code pickupAppointmentStart}/{@code eta} still collapse a multi-stop manifest down to just its <em>first</em>
 * pickup and <em>last</em> dropoff stop, for the Schedule grid's compact per-driver segment bars (unchanged behavior).
 * {@code stops} instead carries every real stop, in order, with the full per-stop detail Vektor reports: a nested
 * location (field 19: {@code 2}/{@code 3} lat/lng, {@code 4} formatted address, {@code 5} timezone abbreviation,
 * {@code 6} site/company name), an appointment window ({@code 24} start, {@code 25} end), actual
 * arrival/check-in/check-out timestamps ({@code 7}/{@code 8}/{@code 9} - null until the driver reaches/checks in/checks
 * out of that stop), combined reference numbers ({@code 26}), free-text notes ({@code 18}), a contact phone number
 * ({@code 36} - present inconsistently), and the outbound leg to the next stop's estimated/actual mileage and odometer
 * reading ({@code 11}/{@code 15}/{@code 13}). All of the above use the same {@code yyyy-MM-dd HH:mm:ss} timestamp
 * format as {@code appointment_start_datetime}.
 */
@Component
public class VektorManifestMapper {

    private static final int STOP_TYPE_PICKUP = 1;
    private static final int STOP_TYPE_DROPOFF = 4;
    private static final int ENTRY_KIND_STARTING_POSITION = 2;
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

        List<VektorGrpcWeb.Message> stopMessages = manifest.getMessages(32);
        VektorGrpcWeb.Message pickup = firstStopOfType(stopMessages, STOP_TYPE_PICKUP);
        VektorGrpcWeb.Message dropoff = lastStopOfType(stopMessages, STOP_TYPE_DROPOFF);

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
                toStops(stopMessages),
                toStartingPosition(stopMessages),
                writeManifestAsJson(manifest),
                null);
    }

    private List<VektorManifestStop> toStops(List<VektorGrpcWeb.Message> stopMessages) {
        return stopMessages.stream()
                .filter(stop -> !isStartingPosition(stop))
                .map(this::toStop)
                .sorted(Comparator.comparingInt(VektorManifestStop::sequenceNumber))
                .toList();
    }

    private @Nullable VektorManifestStartingPosition toStartingPosition(List<VektorGrpcWeb.Message> stopMessages) {
        return stopMessages.stream()
                .filter(this::isStartingPosition)
                .findFirst()
                .map(entry -> {
                    VektorGrpcWeb.Message location = entry.getMessage(19).orElse(null);
                    return new VektorManifestStartingPosition(
                            location == null ? null : location.getString(4).orElse(null),
                            location == null
                                    ? null
                                    : location.getDouble(2)
                                            .map(BigDecimal::valueOf)
                                            .orElse(null),
                            location == null
                                    ? null
                                    : location.getDouble(3)
                                            .map(BigDecimal::valueOf)
                                            .orElse(null),
                            entry.getString(18).orElse(null),
                            entry.getString(11).map(BigDecimal::new).orElse(null),
                            entry.getString(15).map(BigDecimal::new).orElse(null),
                            entry.getString(13).map(BigDecimal::new).orElse(null));
                })
                .orElse(null);
    }

    private boolean isStartingPosition(VektorGrpcWeb.Message stop) {
        return stop.getVarint(1)
                .map(kind -> kind == ENTRY_KIND_STARTING_POSITION)
                .orElse(false);
    }

    private VektorManifestStop toStop(VektorGrpcWeb.Message stop) {
        VektorGrpcWeb.Message location = stop.getMessage(19).orElseThrow();
        return new VektorManifestStop(
                stop.getVarint(6).orElseThrow().intValue(),
                stopType(stop),
                location.getString(6).orElse(null),
                location.getString(4).orElse(null),
                location.getDouble(2).map(BigDecimal::valueOf).orElse(null),
                location.getDouble(3).map(BigDecimal::valueOf).orElse(null),
                location.getString(5).orElse(null),
                stop.getString(24).map(this::parseDateTime).orElse(null),
                stop.getString(25).map(this::parseDateTime).orElse(null),
                stop.getString(7).map(this::parseDateTime).orElse(null),
                stop.getString(8).map(this::parseDateTime).orElse(null),
                stop.getString(9).map(this::parseDateTime).orElse(null),
                stop.getString(26).orElse(null),
                stop.getString(18).orElse(null),
                stop.getString(36).orElse(null),
                stop.getString(11).map(BigDecimal::new).orElse(null),
                stop.getString(15).map(BigDecimal::new).orElse(null),
                stop.getString(13).map(BigDecimal::new).orElse(null));
    }

    private LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(text, APPOINTMENT_DATE_TIME_FORMAT);
    }

    private StopType stopType(VektorGrpcWeb.Message stop) {
        long type = stop.getVarint(22).orElseThrow(() -> new IllegalStateException("stop has no stop type (field 22)"));
        if (type == STOP_TYPE_PICKUP) {
            return StopType.PICKUP;
        }
        if (type == STOP_TYPE_DROPOFF) {
            return StopType.DROPOFF;
        }
        throw new IllegalStateException("unknown stop type " + type + " (field 22)");
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
        return stop.getString(24).map(this::parseDateTime).orElse(null);
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
