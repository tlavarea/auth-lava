package com.lava.swexpedited.controller;

import com.lava.swexpedited.service.TruckRouteHistoryService;
import com.lava.swexpedited.service.TruckSafetyEventsService;
import com.lava.swexpedited.service.TruckService;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import com.lava.swexpedited.truck.TruckRouteHistoryResponse;
import com.lava.swexpedited.truck.TruckSafetyEventEntry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TruckController {

    private final TruckService truckService;
    private final TruckRouteHistoryService truckRouteHistoryService;
    private final TruckSafetyEventsService truckSafetyEventsService;

    public TruckController(
            TruckService truckService,
            TruckRouteHistoryService truckRouteHistoryService,
            TruckSafetyEventsService truckSafetyEventsService) {
        this.truckService = truckService;
        this.truckRouteHistoryService = truckRouteHistoryService;
        this.truckSafetyEventsService = truckSafetyEventsService;
    }

    @GetMapping("/api/trucks")
    public List<TruckListingRow> trucks() {
        return this.truckService.findAll();
    }

    @GetMapping("/api/trucks/{truckId}")
    public ResponseEntity<TruckDetailResponse> truck(@PathVariable String truckId) {
        return this.truckService
                .findDetail(truckId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The truck detail page's route map data (polyline points + clustered stops) for {@code truckId}, defaulting to
     * today (server's local midnight through now) when {@code startTime}/{@code endTime} aren't given - same
     * optional-query-param-with-controller-computed-default pattern as {@code DriverController.timeline()}. 404s only
     * when {@code truckId} itself doesn't resolve to a vektor_truck row - see {@link TruckRouteHistoryService}'s
     * javadoc for why an existing-but-unmatched truck gets an empty 200 instead.
     */
    @GetMapping("/api/trucks/{truckId}/route-history")
    public ResponseEntity<TruckRouteHistoryResponse> routeHistory(
            @PathVariable String truckId,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime) {
        Instant resolvedStart = startTime != null ? startTime : Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant resolvedEnd = endTime != null ? endTime : Instant.now();
        return this.truckRouteHistoryService
                .findRouteHistory(truckId, resolvedStart, resolvedEnd)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Samsara-flagged safety events for {@code truckId} since {@code startTime}, defaulting to today (server's local
     * midnight) when absent - same 404 policy as {@link #routeHistory}.
     */
    @GetMapping("/api/trucks/{truckId}/safety-events")
    public ResponseEntity<List<TruckSafetyEventEntry>> safetyEvents(
            @PathVariable String truckId, @RequestParam(required = false) Instant startTime) {
        Instant resolvedStart = startTime != null ? startTime : Instant.now().truncatedTo(ChronoUnit.DAYS);
        return this.truckSafetyEventsService
                .findSafetyEvents(truckId, resolvedStart)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
