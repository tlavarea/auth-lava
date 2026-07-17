package com.lava.swexpedited.batch.pickupmatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.LatLng;
import com.lava.swexpedited.batch.pickupmatch.RouteMatrixClient.RouteMatrixElement;
import com.lava.swexpedited.boot.autoconfigure.app.PickupMatchProperties;
import com.lava.swexpedited.repository.ShipmentDetailRepository;
import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.repository.VektorManifestRepository;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import com.lava.swexpedited.vektor.VektorManifestRow;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@ExtendWith(MockitoExtension.class)
class PickupMatchTaskletTest {

    private static final ZoneId GFM_ZONE = ZoneId.of("America/New_York");

    @Mock
    private ShipmentListingRepository shipmentListingRepository;

    @Mock
    private ShipmentDetailRepository shipmentDetailRepository;

    @Mock
    private VektorManifestRepository vektorManifestRepository;

    @Mock
    private PickupWindowMapper pickupWindowMapper;

    @Mock
    private RouteMatrixClient routeMatrixClient;

    private final PickupMatchProperties pickupMatchProperties =
            new PickupMatchProperties(Duration.ofHours(48), 500, Duration.ofHours(2));

    @Test
    void execute_manifestWithinTimeAndDistance_marksShipmentAsViablePickup() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        PickupWindow window = new PickupWindow(now.plusDays(1), now.plusDays(2));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(window);
        VektorManifestRow manifest = manifest(100L, now.plusDays(1).plusHours(1), "33.1", "-87.9");
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of(manifest));
        // 100 miles, 2h drive - eta (day+1h) + 2h drive + 2h buffer lands well before the window closes (day+2).
        when(this.routeMatrixClient.computeRouteMatrix(
                        List.of("Bessemer, AL"), List.of(new LatLng(new BigDecimal("33.1"), new BigDecimal("-87.9")))))
                .thenReturn(List.of(new RouteMatrixElement(0, 0, 160934L, "ROUTE_EXISTS", "7200s")));

        PickupMatchTasklet tasklet = tasklet();
        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(this.shipmentListingRepository).markViablePickups(Set.of(1L));
    }

    @Test
    void execute_distanceBeyondMax_doesNotMarkShipment() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        PickupWindow window = new PickupWindow(now.plusDays(1), now.plusDays(2));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(window);
        VektorManifestRow manifest = manifest(100L, now.plusDays(1).plusHours(1), "33.1", "-87.9");
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of(manifest));
        // 800 miles in meters, well over the 500 mile max configured above.
        when(this.routeMatrixClient.computeRouteMatrix(anyList(), anyList()))
                .thenReturn(List.of(new RouteMatrixElement(0, 0, 1287476L, "ROUTE_EXISTS", "43200s")));

        PickupMatchTasklet tasklet = tasklet();
        tasklet.execute(null, null);

        verify(this.shipmentListingRepository).markViablePickups(Set.of());
    }

    @Test
    void execute_driveDurationAndBufferPushArrivalPastWindowClose_doesNotMarkShipment() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        // Narrow window: closes 2h after the driver's eta, leaving no room for a 3h drive plus buffer.
        PickupWindow window = new PickupWindow(now.plusDays(1), now.plusDays(1).plusHours(3));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(window);
        VektorManifestRow manifest = manifest(100L, now.plusDays(1).plusHours(1), "33.1", "-87.9");
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of(manifest));
        // 100 miles but a 3h drive - eta (day+1h) + 3h drive + 2h buffer lands after the window closes (day+3h).
        when(this.routeMatrixClient.computeRouteMatrix(anyList(), anyList()))
                .thenReturn(List.of(new RouteMatrixElement(0, 0, 160934L, "ROUTE_EXISTS", "10800s")));

        PickupMatchTasklet tasklet = tasklet();
        tasklet.execute(null, null);

        verify(this.shipmentListingRepository).markViablePickups(Set.of());
    }

    @Test
    void execute_manifestEtaOutsideTimeWindow_skipsRouteMatrixLookupAndMarksNothing() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        PickupWindow window = new PickupWindow(now.plusDays(1), now.plusDays(2));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(window);
        // ETA a week away - well outside the pickup window plus the 48h configured tolerance.
        VektorManifestRow manifest = manifest(100L, now.plusDays(9), "33.1", "-87.9");
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of(manifest));

        PickupMatchTasklet tasklet = tasklet();
        RepeatStatus status = tasklet.execute(null, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(this.routeMatrixClient, never()).computeRouteMatrix(anyList(), anyList());
        verify(this.shipmentListingRepository, never()).markViablePickups(anySet());
    }

    @Test
    void execute_pickupWindowAlreadyPassed_excludesShipment() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        PickupWindow pastWindow = new PickupWindow(now.minusDays(3), now.minusDays(2));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(pastWindow);
        when(this.vektorManifestRepository.findAll())
                .thenReturn(List.of(manifest(100L, now.minusDays(2).plusHours(1), "33.1", "-87.9")));

        PickupMatchTasklet tasklet = tasklet();
        tasklet.execute(null, null);

        verify(this.routeMatrixClient, never()).computeRouteMatrix(anyList(), anyList());
    }

    @Test
    void execute_manifestMissingDestinationCoordinates_excludedFromCandidates() {
        LocalDateTime now = LocalDateTime.now(GFM_ZONE);
        PickupWindow window = new PickupWindow(now.plusDays(1), now.plusDays(2));
        when(this.shipmentListingRepository.findAll()).thenReturn(List.of(shipment(1L, "Bessemer, AL")));
        when(this.shipmentDetailRepository.findAll()).thenReturn(List.of(detail(1L, "raw-1")));
        when(this.pickupWindowMapper.map("raw-1")).thenReturn(window);
        VektorManifestRow manifestWithoutCoordinates =
                manifest(100L, now.plusDays(1).plusHours(1), null, null);
        when(this.vektorManifestRepository.findAll()).thenReturn(List.of(manifestWithoutCoordinates));

        PickupMatchTasklet tasklet = tasklet();
        tasklet.execute(null, null);

        verify(this.routeMatrixClient, never()).computeRouteMatrix(anyList(), anyList());
    }

    private PickupMatchTasklet tasklet() {
        return new PickupMatchTasklet(
                this.shipmentListingRepository,
                this.shipmentDetailRepository,
                this.vektorManifestRepository,
                this.pickupWindowMapper,
                this.routeMatrixClient,
                this.pickupMatchProperties);
    }

    private ShipmentListingRow shipment(long offerId, String origin) {
        return new ShipmentListingRow(
                offerId,
                "Open",
                null,
                "SHIP" + offerId,
                "FAK",
                "1",
                "GBLOC",
                origin,
                "destination",
                "AF2",
                1,
                0,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(10),
                null,
                false);
    }

    private ShipmentDetailRow detail(long offerId, String rawResponse) {
        return new ShipmentDetailRow(offerId, null, null, null, null, null, null, null, null, null, rawResponse, null);
    }

    private VektorManifestRow manifest(
            long manifestNumber, LocalDateTime eta, String destinationLatitude, String destinationLongitude) {
        return new VektorManifestRow(
                manifestNumber,
                "manifest-" + manifestNumber,
                "driver-uuid",
                "Warren Ruawhare",
                null,
                "manifest_in_progress",
                "origin",
                "destination",
                destinationLatitude == null ? null : new BigDecimal(destinationLatitude),
                destinationLongitude == null ? null : new BigDecimal(destinationLongitude),
                eta,
                "SwX-" + manifestNumber,
                "{}",
                null);
    }
}
