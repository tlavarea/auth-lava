package com.lava.swexpedited.controller;

import com.lava.swexpedited.samsara.DriverActivityEntry;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import com.lava.swexpedited.samsara.DriverTimelineRow;
import com.lava.swexpedited.service.DriverTimelineService;
import com.lava.swexpedited.service.SamsaraDriverActivityService;
import com.lava.swexpedited.service.SamsaraDriverLiveLocationService;
import com.lava.swexpedited.service.SamsaraDriverService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DriverController {

    private static final Duration DEFAULT_ACTIVITY_WINDOW = Duration.ofHours(24);
    private static final Duration DEFAULT_TIMELINE_WINDOW = Duration.ofDays(7);
    private static final Duration MAX_TIMELINE_WINDOW = Duration.ofDays(31);

    private final SamsaraDriverService samsaraDriverService;
    private final SamsaraDriverLiveLocationService samsaraDriverLiveLocationService;
    private final SamsaraDriverActivityService samsaraDriverActivityService;
    private final DriverTimelineService driverTimelineService;

    public DriverController(
            SamsaraDriverService samsaraDriverService,
            SamsaraDriverLiveLocationService samsaraDriverLiveLocationService,
            SamsaraDriverActivityService samsaraDriverActivityService,
            DriverTimelineService driverTimelineService) {
        this.samsaraDriverService = samsaraDriverService;
        this.samsaraDriverLiveLocationService = samsaraDriverLiveLocationService;
        this.samsaraDriverActivityService = samsaraDriverActivityService;
        this.driverTimelineService = driverTimelineService;
    }

    @GetMapping("/api/drivers")
    public List<DriverListingRow> drivers() {
        return samsaraDriverService.findAll();
    }

    /**
     * One row per driver joining current HOS duty status with every vektor_manifest matching them whose scheduled
     * pickup->dropoff window overlaps the requested range - see {@link DriverTimelineService}'s javadoc. Backs the
     * Schedule view's date-range navigation. {@code weekStart} defaults to the start of today when absent, same pattern
     * as {@link #activity}'s {@code since} default; {@code end} defaults to {@code weekStart + 7 days} when absent,
     * matching the view's default one-week window. The frontend clamps its date-range picker to 31 days, but an
     * out-of-range {@code end} is rejected here too rather than trusted, since {@code weekStart}/{@code end} are plain
     * client-suppliable query params. Converted with the system default zone, not UTC - vektor_manifest's
     * pickup_appointment_start/eta are parsed straight from Vektor's raw appointment strings with no timezone
     * conversion of their own (see {@code VektorManifestMapper#parseAppointmentStart}), so there's no established zone
     * convention to match here beyond "wall-clock time as the server sees it".
     */
    @GetMapping("/api/drivers/timeline")
    public List<DriverTimelineRow> timeline(
            @RequestParam(required = false) Instant weekStart, @RequestParam(required = false) Instant end) {
        Instant resolvedStart = weekStart != null ? weekStart : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant resolvedEnd = end != null ? end : resolvedStart.plus(DEFAULT_TIMELINE_WINDOW);
        if (Duration.between(resolvedStart, resolvedEnd).compareTo(MAX_TIMELINE_WINDOW) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested timeline window exceeds the " + MAX_TIMELINE_WINDOW.toDays() + "-day maximum");
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime start = LocalDateTime.ofInstant(resolvedStart, zone);
        LocalDateTime windowEnd = LocalDateTime.ofInstant(resolvedEnd, zone);
        return driverTimelineService.findForWeek(start, windowEnd);
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
