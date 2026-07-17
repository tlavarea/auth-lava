package com.lava.swexpedited.batch.gfm;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentCsvParserTest {

    private final ShipmentCsvParser parser = new ShipmentCsvParser();

    private static final String CSV = """
            Offer,Status,Expiration Date,Shipment ID,Shipment Type,Rank,GBLOC,Origin,Destination,Equip Type,Conveyances Offered/Accepted,Pickup,Required Delivery
            1284311010,Open,03/30/2026 03:33:15 PM EDT,KLFV160850003,FAK,36,KLFV,"774900240, KIRTLAND AFB,NM","773466240, CANNON AFB,NM",AF2,[ 1 / 0 ],03/31/2026,04/09/2026
            1273401686,Awaiting Award,02/25/2026 02:48:49 PM EST,FIAZ560560003,FAK,123,FIAZ,"471356250, REDSTONE ARSENAL W31P38,AL","375350000, BEDFORD,IN",AF3,[ 1 / 1 ],03/09/2026,03/11/2026
            """;

    @Test
    void parse_mapsAllRows() {
        List<ShipmentListingRow> rows = this.parser.parse(CSV.getBytes(StandardCharsets.UTF_8));

        assertThat(rows).hasSize(2);
    }

    @Test
    void parse_mapsColumnsIncludingEmbeddedCommas() {
        List<ShipmentListingRow> rows = this.parser.parse(CSV.getBytes(StandardCharsets.UTF_8));

        ShipmentListingRow first = rows.getFirst();
        assertThat(first.offerId()).isEqualTo(1284311010L);
        assertThat(first.status()).isEqualTo("Open");
        // The column has no timezone (see ShipmentListingRow's javadoc), so this is just the local
        // wall-clock value from the CSV with the trailing zone abbreviation (EDT) dropped.
        assertThat(first.expirationDate()).isEqualTo(LocalDateTime.of(2026, 3, 30, 15, 33, 15));
        assertThat(first.shipmentId()).isEqualTo("KLFV160850003");
        assertThat(first.shipmentType()).isEqualTo("FAK");
        assertThat(first.rank()).isEqualTo("36");
        assertThat(first.gbloc()).isEqualTo("KLFV");
        assertThat(first.origin()).isEqualTo("774900240, KIRTLAND AFB,NM");
        assertThat(first.destination()).isEqualTo("773466240, CANNON AFB,NM");
        assertThat(first.equipType()).isEqualTo("AF2");
        assertThat(first.conveyancesOffered()).isEqualTo(1);
        assertThat(first.conveyancesAccepted()).isEqualTo(0);
        assertThat(first.pickupDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(first.requiredDeliveryDate()).isEqualTo(LocalDate.of(2026, 4, 9));
        assertThat(first.syncedAt()).isNull();
    }

    @Test
    void parse_secondRow_mapsExpirationAndConveyances() {
        List<ShipmentListingRow> rows = this.parser.parse(CSV.getBytes(StandardCharsets.UTF_8));

        ShipmentListingRow second = rows.get(1);
        assertThat(second.expirationDate()).isEqualTo(LocalDateTime.of(2026, 2, 25, 14, 48, 49));
        assertThat(second.conveyancesOffered()).isEqualTo(1);
        assertThat(second.conveyancesAccepted()).isEqualTo(1);
    }
}
