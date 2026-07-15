package com.lava.swexpedited.shipment;

/** One packed item within an {@link EquipmentUnit}, from GFM's {@code equipmentItems[]}. */
public record EquipmentItemDetail(
        String description,
        String packType,
        Integer pieces,
        Integer quantity,
        String quantityUom,
        Integer length,
        Integer width,
        Integer height,
        Integer cubicFeet) {}
