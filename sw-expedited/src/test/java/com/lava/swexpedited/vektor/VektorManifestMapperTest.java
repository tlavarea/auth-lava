package com.lava.swexpedited.vektor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VektorManifestMapperTest {

    private static final String DRIVER_ID = "b4a58cf3-150c-4ab8-9f9a-31a03da29bc2";

    private final VektorManifestMapper vektorManifestMapper = new VektorManifestMapper();

    @Test
    void toRow_pickupAndDropoffStops_mapsOriginDestinationEtaAndDriver() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of(DRIVER_ID, "Warren Ruawhare"));

        assertThat(row.manifestNumber()).isEqualTo(1000589L);
        assertThat(row.manifestId()).isEqualTo("71da0ba8-865b-4c1a-8ad1-b95a4d2b8398");
        assertThat(row.driverId()).isEqualTo(DRIVER_ID);
        assertThat(row.driverName()).isEqualTo("Warren Ruawhare");
        assertThat(row.status()).isEqualTo("manifest_in_progress");
        assertThat(row.origin()).isEqualTo("4251 Turin Dr, Bessemer, AL 35020");
        assertThat(row.destination()).isEqualTo("6390 N Alsup Rd, Litchfield Park, AZ 85340");
        assertThat(row.destinationLatitude()).isEqualByComparingTo(BigDecimal.valueOf(33.5283256d));
        assertThat(row.destinationLongitude()).isEqualByComparingTo(BigDecimal.valueOf(-112.4031521d));
        assertThat(row.pickupAppointmentStart()).isEqualTo(LocalDateTime.of(2026, 7, 17, 8, 0, 0));
        assertThat(row.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0, 0));
        assertThat(row.loadReference()).isEqualTo("SwX-1000589");
        assertThat(row.rawResponse()).isNotBlank().contains("manifest_in_progress");
        assertThat(row.matchedSamsaraDriverId()).isNull();
        assertThat(row.syncedAt()).isNull();
    }

    @Test
    void toRow_driverIdNotInRoster_driverNameIsNull() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of("some-other-uuid", "Someone Else"));

        assertThat(row.driverId()).isEqualTo(DRIVER_ID);
        assertThat(row.driverName()).isNull();
    }

    @Test
    void toRow_multipleDropoffStops_usesLastAsDestination() {
        VektorGrpcWeb.Writer firstDropoff = new VektorGrpcWeb.Writer()
                .writeVarint(1, 1)
                .writeVarint(6, 2)
                .writeVarint(22, 4)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 34.0d)
                                .writeDouble(3, -111.0d)
                                .writeString(4, "Intermediate Stop, AZ"))
                .writeString(24, "2026-07-18 10:00:00");

        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), firstDropoff, dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.destination()).isEqualTo("6390 N Alsup Rd, Litchfield Park, AZ 85340");
        assertThat(row.eta()).isEqualTo(LocalDateTime.of(2026, 7, 20, 10, 0, 0));
    }

    @Test
    void toRow_noPickupStop_originIsNull() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.origin()).isNull();
        assertThat(row.pickupAppointmentStart()).isNull();
        assertThat(row.destination()).isNotNull();
    }

    @Test
    void toRow_stopsWithFullDetail_mapsEveryFieldInOrder() {
        VektorGrpcWeb.Writer startingPosition = new VektorGrpcWeb.Writer()
                .writeVarint(1, 2)
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 31.1929849d)
                                .writeDouble(3, -81.478291d)
                                .writeString(4, "3314 Cypress Mill Rd, Brunswick, GA 31520"))
                .writeString(11, "74.00")
                .writeString(13, "406543")
                .writeString(15, "174.00")
                .writeString(18, "Last stop of previous manifest #1000585");

        VektorGrpcWeb.Writer completedPickup = new VektorGrpcWeb.Writer()
                .writeVarint(1, 1)
                .writeVarint(6, 1)
                .writeVarint(22, 1)
                .writeString(4, "SwX-1000588")
                .writeString(7, "2026-07-17 10:06:14")
                .writeString(8, "2026-07-17 10:06:16")
                .writeString(9, "2026-07-17 11:55:54")
                .writeString(11, "83.00")
                .writeString(13, "406717")
                .writeString(15, "13.00")
                .writeString(18, "Check in no more than 15 minutes before your loading time.")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 32.1679472d)
                                .writeDouble(3, -81.2363794d)
                                .writeString(4, "122 Norwest Ct, Savannah, GA 31407")
                                .writeString(5, "EDT")
                                .writeString(6, "Dealer Warehouse C/O Keen Transport"))
                .writeString(24, "2026-07-17 09:30:00")
                .writeString(25, "2026-07-17 10:00:00")
                .writeString(26, "CO 01660967, MCI 3468605")
                .writeString(36, "+19127483999");

        VektorGrpcWeb.Writer notYetArrivedDropoff = new VektorGrpcWeb.Writer()
                .writeVarint(1, 1)
                .writeVarint(6, 2)
                .writeVarint(22, 4)
                .writeString(4, "SwX-1000588")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 29.5729366d)
                                .writeDouble(3, -97.9394257d)
                                .writeString(4, "1502 E Walnut St, Seguin, TX 78155"))
                .writeString(24, "2026-07-20 08:00:00")
                .writeString(25, "2026-07-21 15:00:00")
                .writeString(26, "CO 03748983");

        VektorGrpcWeb.Message manifest =
                decode(manifestWriter(startingPosition, notYetArrivedDropoff, completedPickup));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.startingPosition().address()).isEqualTo("3314 Cypress Mill Rd, Brunswick, GA 31520");
        assertThat(row.startingPosition().latitude()).isEqualByComparingTo(BigDecimal.valueOf(31.1929849d));
        assertThat(row.startingPosition().longitude()).isEqualByComparingTo(BigDecimal.valueOf(-81.478291d));
        assertThat(row.startingPosition().note()).isEqualTo("Last stop of previous manifest #1000585");
        assertThat(row.startingPosition().estimatedMilesToNext()).isEqualByComparingTo(new BigDecimal("74.00"));
        assertThat(row.startingPosition().actualMilesToNext()).isEqualByComparingTo(new BigDecimal("174.00"));
        assertThat(row.startingPosition().odometerMiles()).isEqualByComparingTo(new BigDecimal("406543"));

        assertThat(row.stops()).hasSize(2);
        assertThat(row.stops()).extracting(VektorManifestStop::sequenceNumber).containsExactly(1, 2);

        VektorManifestStop pickup = row.stops().getFirst();
        assertThat(pickup.stopType()).isEqualTo(StopType.PICKUP);
        assertThat(pickup.siteName()).isEqualTo("Dealer Warehouse C/O Keen Transport");
        assertThat(pickup.address()).isEqualTo("122 Norwest Ct, Savannah, GA 31407");
        assertThat(pickup.timezoneAbbreviation()).isEqualTo("EDT");
        assertThat(pickup.appointmentWindowStart()).isEqualTo(LocalDateTime.of(2026, 7, 17, 9, 30, 0));
        assertThat(pickup.appointmentWindowEnd()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 0, 0));
        assertThat(pickup.arrivedAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 6, 14));
        assertThat(pickup.checkedInAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 6, 16));
        assertThat(pickup.checkedOutAt()).isEqualTo(LocalDateTime.of(2026, 7, 17, 11, 55, 54));
        assertThat(pickup.referenceNumbers()).isEqualTo("CO 01660967, MCI 3468605");
        assertThat(pickup.notes()).isEqualTo("Check in no more than 15 minutes before your loading time.");
        assertThat(pickup.contactPhone()).isEqualTo("+19127483999");
        assertThat(pickup.estimatedMilesToNext()).isEqualByComparingTo(new BigDecimal("83.00"));
        assertThat(pickup.actualMilesToNext()).isEqualByComparingTo(new BigDecimal("13.00"));
        assertThat(pickup.odometerMiles()).isEqualByComparingTo(new BigDecimal("406717"));

        VektorManifestStop dropoff = row.stops().get(1);
        assertThat(dropoff.stopType()).isEqualTo(StopType.DROPOFF);
        assertThat(dropoff.arrivedAt()).isNull();
        assertThat(dropoff.checkedInAt()).isNull();
        assertThat(dropoff.checkedOutAt()).isNull();
        assertThat(dropoff.contactPhone()).isNull();
        assertThat(dropoff.estimatedMilesToNext()).isNull();
        assertThat(dropoff.actualMilesToNext()).isNull();
        assertThat(dropoff.odometerMiles()).isNull();
    }

    @Test
    void toRow_noStartingPositionEntry_startingPositionIsNull() {
        VektorGrpcWeb.Message manifest = decode(manifestWriter(pickupStop(), dropoffStop()));

        VektorManifestRow row = vektorManifestMapper.toRow(manifest, Map.of());

        assertThat(row.startingPosition()).isNull();
    }

    private VektorGrpcWeb.Writer manifestWriter(VektorGrpcWeb.Writer... stops) {
        VektorGrpcWeb.Writer manifest = new VektorGrpcWeb.Writer()
                .writeString(1, "71da0ba8-865b-4c1a-8ad1-b95a4d2b8398")
                .writeVarint(2, 1000589L);
        for (VektorGrpcWeb.Writer stop : stops) {
            manifest.writeMessage(32, stop);
        }
        return manifest.writeString(35, DRIVER_ID).writeString(50, "manifest_in_progress");
    }

    private VektorGrpcWeb.Writer pickupStop() {
        return new VektorGrpcWeb.Writer()
                .writeVarint(1, 1)
                .writeVarint(6, 1)
                .writeVarint(22, 1)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 33.3937585d)
                                .writeDouble(3, -86.9302783d)
                                .writeString(4, "4251 Turin Dr, Bessemer, AL 35020"))
                .writeString(24, "2026-07-17 08:00:00");
    }

    private VektorGrpcWeb.Writer dropoffStop() {
        return new VektorGrpcWeb.Writer()
                .writeVarint(1, 1)
                .writeVarint(6, 3)
                .writeVarint(22, 4)
                .writeString(4, "SwX-1000589")
                .writeMessage(
                        19,
                        new VektorGrpcWeb.Writer()
                                .writeDouble(2, 33.5283256d)
                                .writeDouble(3, -112.4031521d)
                                .writeString(4, "6390 N Alsup Rd, Litchfield Park, AZ 85340"))
                .writeString(24, "2026-07-20 10:00:00");
    }

    private VektorGrpcWeb.Message decode(VektorGrpcWeb.Writer writer) {
        return VektorGrpcWeb.decodeUnaryResponse(VektorGrpcWeb.encodeUnaryResponse(writer));
    }
}
