package com.lava.swexpedited.shipment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The GFM/ATR "getBid" response for one shipment offer, shared by the batch fetch (write side - {@code syncedAt} is
 * null, set by the repository at insert time) and the repository's read side (fully populated). A handful of fields
 * likely to be filtered/sorted/displayed in a summary are pulled out as typed columns; rawResponse holds the entire
 * response body as-is so nothing the UI's detail view needs is lost to under-modeling a deeply nested, DoD-owned
 * payload (see 002-create-shipment-detail.yaml).
 */
public record ShipmentDetailRow(
        long offerId,
        BigDecimal totalAmount,
        BigDecimal lineHaulCost,
        BigDecimal rateUsed,
        String scac,
        String scacName,
        String tenderNumber,
        String equipmentDesc,
        String requestorName,
        String requestorEmail,
        String rawResponse,
        LocalDateTime syncedAt) {}
