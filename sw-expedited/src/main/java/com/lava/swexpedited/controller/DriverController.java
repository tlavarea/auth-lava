package com.lava.swexpedited.controller;

import com.lava.swexpedited.samsara.DriverActivityEntry;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import com.lava.swexpedited.service.SamsaraDriverActivityService;
import com.lava.swexpedited.service.SamsaraDriverLiveLocationService;
import com.lava.swexpedited.service.SamsaraDriverService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriverController {

    private static final Duration DEFAULT_ACTIVITY_WINDOW = Duration.ofHours(24);

    private final SamsaraDriverService samsaraDriverService;
    private final SamsaraDriverLiveLocationService samsaraDriverLiveLocationService;
    private final SamsaraDriverActivityService samsaraDriverActivityService;

    public DriverController(
            SamsaraDriverService samsaraDriverService,
            SamsaraDriverLiveLocationService samsaraDriverLiveLocationService,
            SamsaraDriverActivityService samsaraDriverActivityService) {
        this.samsaraDriverService = samsaraDriverService;
        this.samsaraDriverLiveLocationService = samsaraDriverLiveLocationService;
        this.samsaraDriverActivityService = samsaraDriverActivityService;
    }

    @GetMapping("/api/drivers")
    public List<DriverListingRow> drivers() {
        return samsaraDriverService.findAll();
    }

    @GetMapping("/api/drivers/{driverId}")
    public ResponseEntity<DriverDetailResponse> driver(@PathVariable String driverId) {
        return samsaraDriverService
                .findDetail(driverId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Live, on-demand position for a single driver's currently-assigned vehicle - see
     * {@link SamsaraDriverLiveLocationService}'s javadoc for why this bypasses samsara_vehicle_location. 404s when the
     * driver has no current vehicle assignment, or a live Samsara call returns no GPS payload for that vehicle.
     */
    @GetMapping("/api/drivers/{driverId}/location")
    public ResponseEntity<DriverLiveLocationResponse> liveLocation(@PathVariable String driverId) {
        return samsaraDriverLiveLocationService
                .findLiveLocation(driverId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Newest-first duty-status-change history for {@code driverId}, defaulting to the last 24 hours when {@code since}
     * isn't given - see {@link SamsaraDriverActivityService}'s javadoc for why this is a live, on-demand call rather
     * than reading from a synced table.
     */
    @GetMapping("/api/drivers/{driverId}/activity")
    public List<DriverActivityEntry> activity(
            @PathVariable String driverId, @RequestParam(required = false) Instant since) {
        return samsaraDriverActivityService.findActivity(
                driverId, since != null ? since : Instant.now().minus(DEFAULT_ACTIVITY_WINDOW));
    }
}
