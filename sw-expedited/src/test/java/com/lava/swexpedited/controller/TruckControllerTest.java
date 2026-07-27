package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.service.TruckService;
import com.lava.swexpedited.truck.TruckDetailResponse;
import com.lava.swexpedited.truck.TruckListingRow;
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

    @Test
    void trucks_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trucks")).andExpect(status().isUnauthorized());
    }

    @Test
    void trucks_withValidAccessTokenCookie_returnsListing() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.truckService.findAll())
                .thenReturn(List.of(new TruckListingRow("truck-1", "T1000", 1, "Jane Trucker", "T231 - 53' SDL")));

        this.mockMvc
                .perform(get("/api/trucks").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("truck-1"))
                .andExpect(jsonPath("$[0].truckNumber").value("T1000"))
                .andExpect(jsonPath("$[0].statusCode").value(1))
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
                null);
        when(this.truckService.findDetail("truck-1")).thenReturn(Optional.of(detail));

        this.mockMvc
                .perform(get("/api/trucks/truck-1").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("truck-1"))
                .andExpect(jsonPath("$.vin").value("1FUJA6CV12LM12345"))
                .andExpect(jsonPath("$.currentDriverName").value("Jane Trucker"))
                .andExpect(jsonPath("$.currentTrailerLabel").value("T231 - 53' SDL"));
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
