package com.lava.swexpedited.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.samsara.DriverActivityEntry;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
import com.lava.swexpedited.samsara.DriverLiveLocationResponse;
import com.lava.swexpedited.samsara.DriverTimelineRow;
import com.lava.swexpedited.samsara.DriverTimelineRow.ManifestSegment;
import com.lava.swexpedited.service.DriverTimelineService;
import com.lava.swexpedited.service.SamsaraDriverActivityService;
import com.lava.swexpedited.service.SamsaraDriverLiveLocationService;
import com.lava.swexpedited.service.SamsaraDriverService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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

@WebMvcTest(DriverController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class DriverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SamsaraDriverService samsaraDriverService;

    @MockitoBean
    private SamsaraDriverLiveLocationService samsaraDriverLiveLocationService;

    @MockitoBean
    private SamsaraDriverActivityService samsaraDriverActivityService;

    @MockitoBean
    private DriverTimelineService driverTimelineService;

    @Test
    void drivers_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/drivers")).andExpect(status().isUnauthorized());
    }

    @Test
    void drivers_withValidAccessTokenCookie_returnsListing() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.samsaraDriverService.findAll())
                .thenReturn(List.of(new DriverListingRow(
                        "41000123", "Jane Trucker", "active", "Truck 12", "driving", "Fort Worth, TX")));

        this.mockMvc
                .perform(get("/api/drivers").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("41000123"))
                .andExpect(jsonPath("$[0].name").value("Jane Trucker"))
                .andExpect(jsonPath("$[0].activationStatus").value("active"))
                .andExpect(jsonPath("$[0].currentVehicleName").value("Truck 12"))
                .andExpect(jsonPath("$[0].dutyStatus").value("driving"))
                .andExpect(jsonPath("$[0].currentLocation").value("Fort Worth, TX"));
    }

    @Test
    void driver_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/drivers/41000123")).andExpect(status().isUnauthorized());
    }

    @Test
    void driver_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.samsaraDriverService.findDetail("unknown")).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/drivers/unknown").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void driver_found_returnsDetail() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        DriverDetailResponse detail = new DriverDetailResponse(
                "41000123",
                "Jane Trucker",
                "jtrucker",
                "jane.trucker@example.com",
                "555-0100",
                "D1234567",
                "TX",
                "active",
                "driving",
                2_000L,
                3_000L,
                4_000L,
                1_000L,
                LocalDateTime.now().minusMinutes(103),
                "expedited",
                "281474",
                "Truck 12",
                new BigDecimal("32.735000"),
                new BigDecimal("-97.108000"),
                new BigDecimal("180.50"),
                new BigDecimal("62.30"),
                LocalDateTime.now(),
                "Fort Worth, TX",
                "{\"id\":\"41000123\"}",
                LocalDateTime.now());
        when(this.samsaraDriverService.findDetail("41000123")).thenReturn(Optional.of(detail));

        this.mockMvc
                .perform(get("/api/drivers/41000123").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("41000123"))
                .andExpect(jsonPath("$.currentVehicleName").value("Truck 12"))
                .andExpect(jsonPath("$.dutyStatus").value("driving"))
                .andExpect(jsonPath("$.formattedLocation").value("Fort Worth, TX"))
                .andExpect(jsonPath("$.rawResponse.id").value("41000123"));
    }

    @Test
    void timeline_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/drivers/timeline")).andExpect(status().isUnauthorized());
    }

    @Test
    void timeline_withValidAccessTokenCookie_returnsTimeline() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.driverTimelineService.findForWeek(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(new DriverTimelineRow(
                        "41000123",
                        "Jane Trucker",
                        "active",
                        "driving",
                        List.of(new ManifestSegment(
                                1000589L,
                                "manifest_in_progress",
                                LocalDateTime.of(2026, 7, 17, 8, 0, 0),
                                LocalDateTime.of(2026, 7, 20, 10, 0, 0),
                                "4251 Turin Dr, Bessemer, AL 35020",
                                "6390 N Alsup Rd, Litchfield Park, AZ 85340",
                                "SwX-1000589")))));

        this.mockMvc
                .perform(get("/api/drivers/timeline").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("41000123"))
                .andExpect(jsonPath("$[0].dutyStatus").value("driving"))
                .andExpect(jsonPath("$[0].manifests[0].manifestNumber").value(1000589))
                .andExpect(jsonPath("$[0].manifests[0].manifestStatus").value("manifest_in_progress"))
                .andExpect(jsonPath("$[0].manifests[0].origin").value("4251 Turin Dr, Bessemer, AL 35020"))
                .andExpect(jsonPath("$[0].manifests[0].loadReference").value("SwX-1000589"));
    }

    @Test
    void liveLocation_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/drivers/41000123/location")).andExpect(status().isUnauthorized());
    }

    @Test
    void liveLocation_noCurrentAssignment_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.samsaraDriverLiveLocationService.findLiveLocation("41000123")).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/drivers/41000123/location").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void liveLocation_found_returnsLiveLocation() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        DriverLiveLocationResponse liveLocation = new DriverLiveLocationResponse(
                new BigDecimal("32.735000"),
                new BigDecimal("-97.108000"),
                new BigDecimal("180.50"),
                new BigDecimal("62.30"),
                LocalDateTime.now(),
                "Fort Worth, TX");
        when(this.samsaraDriverLiveLocationService.findLiveLocation("41000123")).thenReturn(Optional.of(liveLocation));

        this.mockMvc
                .perform(get("/api/drivers/41000123/location").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(32.735000))
                .andExpect(jsonPath("$.formattedLocation").value("Fort Worth, TX"));
    }

    @Test
    void activity_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/drivers/41000123/activity")).andExpect(status().isUnauthorized());
    }

    @Test
    void activity_found_returnsActivityFeed() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        DriverActivityEntry entry = new DriverActivityEntry(
                "driving",
                Instant.now().minusSeconds(3600),
                null,
                new BigDecimal("32.735000"),
                new BigDecimal("-97.108000"),
                null);
        when(this.samsaraDriverActivityService.findActivity(eq("41000123"), any(Instant.class)))
                .thenReturn(List.of(entry));

        this.mockMvc
                .perform(get("/api/drivers/41000123/activity").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dutyStatus").value("driving"));
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
