package com.lava.swexpedited.shipment;

import java.util.List;

/** One equipment-level unit ("Shipment Details" section), from GFM's {@code equipment.equipmentLevelUnits[]}. */
public record EquipmentUnit(
        String ciic,
        String commodityCode,
        String commodityDesc,
        String nsn,
        Integer quantity,
        String quantityUom,
        List<EquipmentItemDetail> items) {}
