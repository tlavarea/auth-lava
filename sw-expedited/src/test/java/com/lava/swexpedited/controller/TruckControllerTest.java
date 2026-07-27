package com.lava.swexpedited.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.service.TruckRouteHistoryService;
import com.lava.swexpedited.service.TruckSafetyEventsService;
import com.lava.swexpedited.service.TruckService;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
import com.lava.swexpedited.truck.TruckRouteHistoryResponse;
import com.lava.swexpedited.truck.TruckRoutePoint;
import com.lava.swexpedited.truck.TruckRouteStop;
import com.lava.swexpedited.truck.TruckSafetyEventEntry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TruckController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class TruckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TruckService truckService;

    @MockitoBean
    private TruckRouteHistoryService truckRouteHistoryService;

    @MockitoBean
    private TruckSafetyEventsService truckSafetyEventsService;

    @Test
    void trucks_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trucks")).andExpect(status().isUnauthorized());
    }

    @Test
    void trucks_withValidAccessTokenCookie_returnsListing() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.truckService.findAll())
                .thenReturn(
                        List.of(new TruckListingRow("truck-1", "T1000", "On", 62.5, "Jane Trucker", "T231 - 53' SDL")));

        this.mockMvc
                .perform(get("/api/trucks").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("truck-1"))
                .andExpect(jsonPath("$[0].truckNumber").value("T1000"))
                .andExpect(jsonPath("$[0].engineState").value("On"))
                .andExpect(jsonPath("$[0].ecuSpeedMph").value(62.5))
                .andExpect(jsonPath("$[0].currentDriverName").value("Jane Trucker"))
                .andExpect(jsonPath("$[0].currentTrailerLabel").value("T231 - 53' SDL"));
    }

    @Test
    void truck_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trucks/truck-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void truck_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.truckService.findDetail("unknown")).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/trucks/unknown").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void truck_found_returnsDetail() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        TruckDetailResponse detail = new TruckDetailResponse(
                "truck-1",
                "T1000",
                1,
                "1FUJA6CV12LM12345",
                "6YA522",
                "Freightliner",
                "Cascadia",
                2023,
                "Jane Trucker",
                "T231 - 53' SDL",
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(this.truckService.findDetail("truck-1")).thenReturn(Optional.of(detail));

        this.mockMvc
                .perform(get("/api/trucks/truck-1").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("truck-1"))
                .andExpect(jsonPath("$.vin").value("1FUJA6CV12LM12345"))
                .andExpect(jsonPath("$.licensePlate").value("6YA522"))
                .andExpect(jsonPath("$.currentDriverName").value("Jane Trucker"))
                .andExpect(jsonPath("$.currentTrailerLabel").value("T231 - 53' SDL"));
    }

    @Test
    void routeHistory_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trucks/truck-1/route-history")).andExpect(status().isUnauthorized());
    }

    @Test
    void routeHistory_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.truckRouteHistoryService.findRouteHistory(eq("unknown"), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/trucks/unknown/route-history").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void routeHistory_found_returnsPointsAndStops() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        TruckRouteHistoryResponse response = new TruckRouteHistoryResponse(
                List.of(new TruckRoutePoint(Instant.now(), 32.735, -97.108, 180, 62.3)),
                List.of(new TruckRouteStop(
                        32.735, -97.108, "Fort Worth, TX", Instant.now().minusSeconds(600), Instant.now(), 10L)));
        when(this.truckRouteHistoryService.findRouteHistory(eq("truck-1"), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(response));

        this.mockMvc
                .perform(get("/api/trucks/truck-1/route-history").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].latitude").value(32.735))
                .andExpect(jsonPath("$.stops[0].formattedLocation").value("Fort Worth, TX"))
                .andExpect(jsonPath("$.stops[0].stoppedMinutes").value(10));
    }

    @Test
    void routeHistory_withStartAndEndParams_passesResolvedWindowToService() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        Instant startTime = Instant.parse("2026-07-27T00:00:00Z");
        Instant endTime = Instant.parse("2026-07-27T12:00:00Z");
        when(this.truckRouteHistoryService.findRouteHistory("truck-1", startTime, endTime))
                .thenReturn(Optional.of(new TruckRouteHistoryResponse(List.of(), List.of())));

        this.mockMvc
                .perform(get("/api/trucks/truck-1/route-history")
                        .param("startTime", startTime.toString())
                        .param("endTime", endTime.toString())
                        .cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk());

        verify(this.truckRouteHistoryService).findRouteHistory("truck-1", startTime, endTime);
    }

    @Test
    void safetyEvents_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trucks/truck-1/safety-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void safetyEvents_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.truckSafetyEventsService.findSafetyEvents(eq("unknown"), any(Instant.class)))
                .thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/trucks/unknown/safety-events").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void safetyEvents_found_returnsEntries() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        TruckSafetyEventEntry entry = new TruckSafetyEventEntry(
                "evt-1",
                Instant.now(),
                List.of("Harsh Brake"),
                32.735,
                -97.108,
                "100 Main St, Fort Worth, TX",
                "Jane Trucker",
                "https://example.com/clip.mp4");
        when(this.truckSafetyEventsService.findSafetyEvents(eq("truck-1"), any(Instant.class)))
                .thenReturn(Optional.of(List.of(entry)));

        this.mockMvc
                .perform(get("/api/trucks/truck-1/safety-events").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("evt-1"))
                .andExpect(jsonPath("$[0].behaviorLabels[0]").value("Harsh Brake"))
                .andExpect(jsonPath("$[0].driverName").value("Jane Trucker"))
                .andExpect(jsonPath("$[0].mediaUrl").value("https://example.com/clip.mp4"));
    }

    private Jwt authenticatedJwt() {
        return Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject("42")
                .claim("authorities", List.of("ROLE_MEMBER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
