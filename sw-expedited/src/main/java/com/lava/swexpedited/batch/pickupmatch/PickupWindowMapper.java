package com.lava.swexpedited.batch.pickupmatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.gfm.model.Bid;
import com.lava.swexpedited.gfm.model.Equipment;
import com.lava.swexpedited.gfm.model.GfmGetBidResponse;
import com.lava.swexpedited.gfm.model.GfmShipment;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Parses a shipment's precise pickup window -
 * {@code bid.equipment.shipment.earliestPickupDate}/{@code latestPickupDate}, epoch milliseconds - out of the same
 * {@code shipment_detail.raw_response} JSON that {@code GfmBidDetailMapper} already reparses for the detail page's
 * display-string counterparts of these same two fields. Builds its own {@link ObjectMapper} for the same reason
 * {@code GfmBidClient}/{@code GfmBidDetailMapper} do - see {@code GfmBidClient}'s javadoc.
 *
 * <p>Converted to America/New_York {@link LocalDateTime}, matching {@code ShipmentCsvParser}'s documented assumption
 * that every timestamp GFM sends is US Eastern wall-clock time - this makes the result directly comparable to
 * {@code vektor_manifest.eta}, itself a naive {@code LocalDateTime} with no zone conversion applied when
 * {@code VektorManifestMapper} parses it. This is a best-effort cross-system assumption (Vektor's appointment times
 * aren't documented as being in any particular zone) rather than a guarantee, consistent with the rest of this batch
 * pipeline's existing timezone handling.
 */
@Component
public class PickupWindowMapper {

    private static final ZoneId GFM_ZONE = ZoneId.of("America/New_York");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Returns {@code null} if either pickup-window field is absent from the response. */
    public @Nullable PickupWindow map(String rawResponse) {
        GfmGetBidResponse response;
        try {
            response = this.objectMapper.readValue(rawResponse, GfmGetBidResponse.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to parse stored raw_response", e);
        }

        Bid bid = response.getBid() != null ? response.getBid() : new Bid();
        Equipment equipment = bid.getEquipment() != null ? bid.getEquipment() : new Equipment();
        GfmShipment shipment = equipment.getShipment() != null ? equipment.getShipment() : new GfmShipment();

        Long earliest = shipment.getEarliestPickupDate();
        Long latest = shipment.getLatestPickupDate();
        if (earliest == null || latest == null) {
            return null;
        }

        return new PickupWindow(toEasternLocalDateTime(earliest), toEasternLocalDateTime(latest));
    }

    private LocalDateTime toEasternLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), GFM_ZONE);
    }
}
