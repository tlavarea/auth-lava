package com.lava.swexpedited.shipment;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The {@code GET /api/shipments/{offerId}} response body: the listing row plus whatever bid detail is available.
 * {@code rawResponse} and the other detail fields are null when the listing exists but its detail hasn't synced yet
 * (e.g. the detail-fetch step skipped it after a failed GFM call) - the frontend can treat a null rawResponse as "not
 * yet available" rather than the request having failed outright. Same for {@code bidDetail}: null exactly when
 * {@code rawResponse} is null, since it's derived from it (see {@link GfmBidDetailMapper}).
 *
 * <p>rawResponse is written with {@link JsonRawValue} rather than parsed back into a Java object graph, since it's
 * already valid JSON text as stored in shipment_detail.raw_response - re-parsing it just to re-serialize the same bytes
 * would be redundant.
 */
public record ShipmentDetailResponse(
        ShipmentListingRow listing,
        BigDecimal totalAmount,
        BigDecimal lineHaulCost,
        BigDecimal rateUsed,
        String scac,
        String scacName,
        String tenderNumber,
        String equipmentDesc,
        String requestorName,
        String requestorEmail,
        @JsonRawValue String rawResponse,
        LocalDateTime syncedAt,
        GfmBidDetail bidDetail) {}
