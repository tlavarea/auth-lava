package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.samsara.DriverDetailResponse;
import com.lava.swexpedited.samsara.DriverListingRow;
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
