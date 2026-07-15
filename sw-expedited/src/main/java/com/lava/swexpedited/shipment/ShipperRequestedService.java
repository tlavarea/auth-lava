package com.lava.swexpedited.shipment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One "Shipper Requested Services" row, from GFM's {@code bid.carrierBidServices[]}. {@code params}' shape is
 * unconfirmed against real data (every sample pulled from sw_expedited_db had an empty {@code carrierBidServiceParams}
 * array despite the ATR UI showing a populated sub-row example) so each entry is passed through as a raw map rather
 * than a typed shape - revisit once a real populated example is available.
 */
public record ShipperRequestedService(
        String description, String code, BigDecimal cost, List<Map<String, Object>> params) {}
