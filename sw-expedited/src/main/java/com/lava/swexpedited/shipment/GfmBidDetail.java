package com.lava.swexpedited.shipment;

import java.math.BigDecimal;
import java.util.List;

/**
 * The read-only "extra" shipment-offer fields the detail page shows beyond the 9 fields {@link ShipmentDetailRow}
 * already persists as typed columns - derived at read time from the same {@code raw_response} JSON by
 * {@link GfmBidDetailMapper}, not persisted itself. Null when {@code rawResponse} is null (detail not yet synced),
 * matching {@link ShipmentDetailResponse}'s existing null-when-not-yet-synced contract.
 */
public record GfmBidDetail(
        Integer bidRank,
        String requestorPhone,
        String originAddress,
        String destinationAddress,
        String earliestPickupDisplay,
        String latestPickupDisplay,
        String latestDeliveryDisplay,
        String offerExpirationDisplay,
        Integer quantity,
        String quantityUom,
        String commodityCode,
        String ratedCommodityCode,
        Integer numberOfConveyances,
        String shipmentMode,
        String remarks,
        String sdg3Remarks,
        String contractNumber,
        String carrierPhone,
        String tenderEffectiveDate,
        String tenderExpirationDate,
        Integer ratedMiles,
        String rateQualifier,
        String ratedQuantityLimits,
        BigDecimal serviceCost,
        BigDecimal miscCost,
        BigDecimal fuelAdjustment,
        String rins,
        List<ShipperRequestedService> shipperRequestedServices,
        List<EquipmentUnit> equipmentUnits) {}
