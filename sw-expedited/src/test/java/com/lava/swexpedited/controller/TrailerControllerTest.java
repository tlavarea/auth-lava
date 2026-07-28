package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.service.TrailerService;
import com.lava.swexpedited.trailer.TrailerDetailResponse;
import com.lava.swexpedited.trailer.TrailerListingRow;
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

@WebMvcTest(TrailerController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class TrailerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TrailerService trailerService;

    @Test
    void trailers_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trailers")).andExpect(status().isUnauthorized());
    }

    @Test
    void trailers_withValidAccessTokenCookie_returnsListing() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.trailerService.findAll())
                .thenReturn(List.of(new TrailerListingRow("trailer-1", "T231 - 53' SDL", "Great Dane", 2022, "T1000")));

        this.mockMvc
                .perform(get("/api/trailers").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("trailer-1"))
                .andExpect(jsonPath("$[0].label").value("T231 - 53' SDL"))
                .andExpect(jsonPath("$[0].manufacturer").value("Great Dane"))
                .andExpect(jsonPath("$[0].year").value(2022))
                .andExpect(jsonPath("$[0].currentTruckNumber").value("T1000"));
    }

    @Test
    void trailer_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/trailers/trailer-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void trailer_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.trailerService.findDetail("unknown")).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/trailers/unknown").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void trailer_found_returnsDetail() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        TrailerDetailResponse detail = new TrailerDetailResponse(
                "trailer-1",
                "T231 - 53' SDL",
                "Great Dane",
                2022,
                "5MC125315H5165489",
                "34A1W4",
                "5MC125315H5165489",
                "T1000",
                "Jane Trucker",
                LocalDateTime.now());
        when(this.trailerService.findDetail("trailer-1")).thenReturn(Optional.of(detail));

        this.mockMvc
                .perform(get("/api/trailers/trailer-1").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("trailer-1"))
                .andExpect(jsonPath("$.label").value("T231 - 53' SDL"))
                .andExpect(jsonPath("$.manufacturer").value("Great Dane"))
                .andExpect(jsonPath("$.vin").value("5MC125315H5165489"))
                .andExpect(jsonPath("$.licensePlate").value("34A1W4"))
                .andExpect(jsonPath("$.assetSerialNumber").value("5MC125315H5165489"))
                .andExpect(jsonPath("$.currentTruckNumber").value("T1000"))
                .andExpect(jsonPath("$.currentDriverName").value("Jane Trucker"));
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
