package com.lava.swexpedited.shipment;

/** Request body for {@code POST /api/shipments/{offerId}/respond}. */
public record OfferResponseRequest(OfferResponseType response, Integer conveyancesAvailable) {}
