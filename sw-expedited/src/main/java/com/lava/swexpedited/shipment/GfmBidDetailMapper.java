package com.lava.swexpedited.shipment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.gfm.model.Bid;
import com.lava.swexpedited.gfm.model.Equipment;
import com.lava.swexpedited.gfm.model.GfmGetBidResponse;
import com.lava.swexpedited.gfm.model.GfmShipment;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives {@link GfmBidDetail} - every shipment-offer field the detail page shows beyond the 9 fields
 * {@link GfmBidClient} (batch package) already persists as typed columns - by re-parsing the same {@code raw_response}
 * JSON already stored for every synced shipment. No schema migration or re-sync needed: {@code raw_response} is already
 * fully persisted for every existing row.
 *
 * <p>Builds its own {@link ObjectMapper} for the same reason {@code GfmBidClient} does - see its Javadoc.
 */
@Component
public class GfmBidDetailMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public GfmBidDetail map(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }

        GfmGetBidResponse response;
        try {
            response = objectMapper.readValue(rawResponse, GfmGetBidResponse.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to parse stored raw_response", e);
        }

        Bid bid = response.getBid() != null ? response.getBid() : new Bid();
        Equipment equipment = bid.getEquipment() != null ? bid.getEquipment() : new Equipment();
        GfmShipment shipment = equipment.getShipment() != null ? equipment.getShipment() : new GfmShipment();

        return new GfmBidDetail(
                bid.getRank(),
                shipment.getRequestorPhone(),
                address(
                        shipment.getOriginSplc(),
                        shipment.getOriginName(),
                        shipment.getOriginAddress1(),
                        shipment.getOriginAddress2(),
                        shipment.getOriginCity(),
                        shipment.getOriginState(),
                        shipment.getOriginZip(),
                        shipment.getOriginCountry()),
                address(
                        shipment.getDestinationSplc(),
                        shipment.getDestinationName(),
                        shipment.getDestinationAddress1(),
                        shipment.getDestinationAddress2(),
                        shipment.getDestinationCity(),
                        shipment.getDestinationState(),
                        shipment.getDestinationZip(),
                        shipment.getDestinationCountry()),
                shipment.getEarliestPickupDateDisplay(),
                shipment.getLatestPickupDateDisplay(),
                shipment.getDeliveryDueDateDisplay(),
                shipment.getOfferExpirationTimeDisplay(),
                equipment.getQuantity(),
                equipment.getQuantityUom(),
                equipment.getCommodityCode(),
                bid.getCommodityCode(),
                equipment.getNumOfEquipment(),
                shipment.getAtrModeDesc(),
                shipment.getRemarks(),
                bid.getSdg3Comments(),
                bid.getContractNumber(),
                bid.getCarrierPhone(),
                bid.getTenderEffectiveDate(),
                bid.getTenderExpirationDate(),
                bid.getRatedMiles(),
                bid.getRateQualifier(),
                bid.getRatedQuantityLimits(),
                bid.getServiceCost(),
                bid.getMiscCost(),
                fuelAdjustment(bid),
                bid.getRinList(),
                services(bid),
                units(equipment));
    }

    private BigDecimal fuelAdjustment(Bid bid) {
        if (bid.getFuelAdjust() != null) {
            return bid.getFuelAdjust();
        }
        if (bid.getFuelCost() != null) {
            return bid.getFuelCost();
        }
        return bid.getFuelSurcharge();
    }

    private String address(
            String splc,
            String name,
            String address1,
            String address2,
            String city,
            String state,
            String zip,
            String country) {
        List<String> lines = new ArrayList<>();
        if (splc != null && !splc.isBlank()) {
            lines.add("SPLC " + splc);
        }
        for (String line : List.of(
                nullToEmpty(name),
                nullToEmpty(address1),
                nullToEmpty(address2),
                nullToEmpty(city),
                nullToEmpty(state),
                nullToEmpty(zip),
                nullToEmpty(country))) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<ShipperRequestedService> services(Bid bid) {
        if (bid.getCarrierBidServices() == null) {
            return List.of();
        }
        List<ShipperRequestedService> services = new ArrayList<>();
        bid.getCarrierBidServices().forEach(service -> {
            List<Map<String, Object>> params = new ArrayList<>();
            if (service.getCarrierBidServiceParams() != null) {
                service.getCarrierBidServiceParams().forEach(param -> params.add(param.getAdditionalProperties()));
            }
            services.add(new ShipperRequestedService(
                    service.getServiceDesc(), service.getServiceCode(), service.getCharge(), params));
        });
        return services;
    }

    private List<EquipmentUnit> units(Equipment equipment) {
        if (equipment.getEquipmentLevelUnits() == null) {
            return List.of();
        }
        List<EquipmentUnit> units = new ArrayList<>();
        equipment.getEquipmentLevelUnits().forEach(unit -> {
            List<EquipmentItemDetail> items = new ArrayList<>();
            if (unit.getEquipmentItems() != null) {
                unit.getEquipmentItems()
                        .forEach(item -> items.add(new EquipmentItemDetail(
                                item.getDescription(),
                                item.getPackType(),
                                item.getPackCount(),
                                item.getQuantity(),
                                item.getQuantityUom(),
                                item.getLength(),
                                item.getWidth(),
                                item.getHeight(),
                                item.getCube())));
            }
            units.add(new EquipmentUnit(
                    unit.getCiic(),
                    unit.getCommodityCode(),
                    unit.getCommodityDesc(),
                    unit.getNsn(),
                    unit.getQuantity(),
                    unit.getQuantityUom(),
                    items));
        });
        return units;
    }
}
