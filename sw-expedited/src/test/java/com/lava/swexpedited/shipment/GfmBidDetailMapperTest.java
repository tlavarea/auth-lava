package com.lava.swexpedited.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GfmBidDetailMapperTest {

    // Field names and shape confirmed against real synced payloads in sw_expedited_db.shipment_detail.raw_response;
    // values here are fabricated, not real DoD shipment/personnel data.
    private static final String FULL_BID_RESPONSE = """
            {
              "bid": {
                "rank": 18,
                "commodityCode": "999913",
                "contractNumber": null,
                "carrierPhone": "4802692601",
                "ratedMiles": 319,
                "rateQualifier": "PM",
                "ratedQuantityLimits": null,
                "serviceCost": 135.00,
                "miscCost": 0,
                "fuelAdjust": 122.07,
                "tenderEffectiveDate": "2026-04-09",
                "tenderExpirationDate": "2028-04-07",
                "sdg3Comments": null,
                "rinList": "105,111,120,123,131,141,332,351",
                "carrierBidServices": [
                  {
                    "serviceDesc": "Protective Tarping",
                    "serviceCode": "PTS",
                    "charge": 100.00,
                    "carrierBidServiceParams": []
                  },
                  {
                    "serviceDesc": null,
                    "serviceCode": "405",
                    "charge": 122.07,
                    "carrierBidServiceParams": []
                  }
                ],
                "equipment": {
                  "quantity": 30000,
                  "quantityUom": "LB",
                  "commodityCode": "999912",
                  "numOfEquipment": 1,
                  "equipmentLevelUnits": [
                    {
                      "ciic": "U",
                      "commodityCode": "999912",
                      "commodityDesc": "FAK (See MFTRP 1C for Cargo Codes)",
                      "nsn": null,
                      "quantity": 30000,
                      "quantityUom": "LB",
                      "equipmentItems": [
                        {
                          "description": "SQUADRON GEAR",
                          "packType": "MX",
                          "packCount": 1,
                          "quantity": 30000,
                          "quantityUom": "LB",
                          "length": 0,
                          "width": 0,
                          "height": 0,
                          "cube": 3300
                        }
                      ]
                    }
                  ],
                  "shipment": {
                    "gbloc": "LUNC",
                    "requestorName": "Jane Doe",
                    "requestorEmail": "jane.doe.civ@us.navy.mil",
                    "requestorPhone": "6195568965",
                    "remarks": "REQUESTING TRUCK AT NASNI PIER LIMA NLT 0800 ON 17JUL2026",
                    "atrModeDesc": "Truckload",
                    "offerExpirationTimeDisplay": "Expired",
                    "earliestPickupDateDisplay": "07/17/2026 08:00 AM",
                    "latestPickupDateDisplay": "07/17/2026 03:00 PM",
                    "deliveryDueDateDisplay": "07/20/2026 12:00 PM",
                    "originSplc": "889000000",
                    "originName": "USS CARL VINSON - NASNI PIER LIMA",
                    "originAddress1": null,
                    "originAddress2": "POC LS2 LEE 901-653-7843",
                    "originCity": "SAN DIEGO",
                    "originState": "California",
                    "originZip": "92135",
                    "originCountry": "United States",
                    "destinationSplc": "879544000",
                    "destinationName": "VFA-122 NAS LEMOORE",
                    "destinationAddress1": "210 REEVES BLVD HANGAR 1",
                    "destinationAddress2": "POC LS1 BECKER 559-998-1738",
                    "destinationCity": "LEMOORE",
                    "destinationState": "California",
                    "destinationZip": "93246",
                    "destinationCountry": "United States"
                  }
                }
              }
            }
            """;

    private final GfmBidDetailMapper mapper = new GfmBidDetailMapper();

    @Test
    void map_nullRawResponse_returnsNull() {
        assertThat(mapper.map(null)).isNull();
    }

    @Test
    void map_missingBid_returnsAllNullFields() {
        GfmBidDetail detail = mapper.map("{}");

        assertThat(detail).isNotNull();
        assertThat(detail.bidRank()).isNull();
        assertThat(detail.originAddress()).isNull();
        assertThat(detail.shipperRequestedServices()).isEmpty();
        assertThat(detail.equipmentUnits()).isEmpty();
    }

    @Test
    void map_fullResponse_parsesScalarFields() {
        GfmBidDetail detail = mapper.map(FULL_BID_RESPONSE);

        assertThat(detail.bidRank()).isEqualTo(18);
        assertThat(detail.requestorPhone()).isEqualTo("6195568965");
        assertThat(detail.earliestPickupDisplay()).isEqualTo("07/17/2026 08:00 AM");
        assertThat(detail.latestPickupDisplay()).isEqualTo("07/17/2026 03:00 PM");
        assertThat(detail.latestDeliveryDisplay()).isEqualTo("07/20/2026 12:00 PM");
        assertThat(detail.offerExpirationDisplay()).isEqualTo("Expired");
        assertThat(detail.quantity()).isEqualTo(30000);
        assertThat(detail.quantityUom()).isEqualTo("LB");
        assertThat(detail.commodityCode()).isEqualTo("999912");
        assertThat(detail.ratedCommodityCode()).isEqualTo("999913");
        assertThat(detail.numberOfConveyances()).isEqualTo(1);
        assertThat(detail.shipmentMode()).isEqualTo("Truckload");
        assertThat(detail.remarks()).contains("REQUESTING TRUCK");
        assertThat(detail.contractNumber()).isNull();
        assertThat(detail.carrierPhone()).isEqualTo("4802692601");
        assertThat(detail.tenderEffectiveDate()).isEqualTo("2026-04-09");
        assertThat(detail.tenderExpirationDate()).isEqualTo("2028-04-07");
        assertThat(detail.ratedMiles()).isEqualTo(319);
        assertThat(detail.rateQualifier()).isEqualTo("PM");
        assertThat(detail.ratedQuantityLimits()).isNull();
        assertThat(detail.serviceCost()).isEqualByComparingTo(new BigDecimal("135.00"));
        assertThat(detail.miscCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(detail.fuelAdjustment()).isEqualByComparingTo(new BigDecimal("122.07"));
        assertThat(detail.rins()).isEqualTo("105,111,120,123,131,141,332,351");
    }

    @Test
    void map_composesMultiLineAddressesSkippingBlankLines() {
        GfmBidDetail detail = mapper.map(FULL_BID_RESPONSE);

        assertThat(detail.originAddress()).isEqualTo("""
                        SPLC 889000000
                        USS CARL VINSON - NASNI PIER LIMA
                        POC LS2 LEE 901-653-7843
                        SAN DIEGO
                        California
                        92135
                        United States""");
        assertThat(detail.destinationAddress()).startsWith("SPLC 879544000\nVFA-122 NAS LEMOORE\n");
    }

    @Test
    void map_shipperRequestedServices_formatsDescriptionWithCode() {
        GfmBidDetail detail = mapper.map(FULL_BID_RESPONSE);

        assertThat(detail.shipperRequestedServices()).hasSize(2);
        ShipperRequestedService tarping = detail.shipperRequestedServices().get(0);
        assertThat(tarping.description()).isEqualTo("Protective Tarping");
        assertThat(tarping.code()).isEqualTo("PTS");
        assertThat(tarping.cost()).isEqualByComparingTo(new BigDecimal("100.00"));

        ShipperRequestedService fuelLine = detail.shipperRequestedServices().get(1);
        assertThat(fuelLine.description()).isNull();
        assertThat(fuelLine.code()).isEqualTo("405");
    }

    @Test
    void map_equipmentUnits_includesNestedItems() {
        GfmBidDetail detail = mapper.map(FULL_BID_RESPONSE);

        assertThat(detail.equipmentUnits()).hasSize(1);
        EquipmentUnit unit = detail.equipmentUnits().get(0);
        assertThat(unit.ciic()).isEqualTo("U");
        assertThat(unit.commodityCode()).isEqualTo("999912");
        assertThat(unit.commodityDesc()).isEqualTo("FAK (See MFTRP 1C for Cargo Codes)");
        assertThat(unit.items()).hasSize(1);
        EquipmentItemDetail item = unit.items().get(0);
        assertThat(item.description()).isEqualTo("SQUADRON GEAR");
        assertThat(item.packType()).isEqualTo("MX");
        assertThat(item.pieces()).isEqualTo(1);
        assertThat(item.cubicFeet()).isEqualTo(3300);
    }
}
