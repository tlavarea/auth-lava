package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.manifest.ManifestDriverLocationResponse;
import com.lava.swexpedited.manifest.ManifestEtaResponse;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.manifest.ManifestStartingPositionResponse;
import com.lava.swexpedited.manifest.ManifestStopResponse;
import com.lava.swexpedited.service.ManifestDriverLocationService;
import com.lava.swexpedited.service.ManifestEtaService;
import com.lava.swexpedited.service.ManifestRouteService;
import com.lava.swexpedited.vektor.StopType;
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

@WebMvcTest(ManifestController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ManifestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ManifestRouteService manifestRouteService;

    @MockitoBean
    private ManifestDriverLocationService manifestDriverLocationService;

    @MockitoBean
    private ManifestEtaService manifestEtaService;

    @Test
    void route_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/manifests/1000589/route")).andExpect(status().isUnauthorized());
    }

    @Test
    void route_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.manifestRouteService.findRoute(1000589L)).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/manifests/1000589/route").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void route_found_returnsRoute() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        ManifestStopResponse stop = new ManifestStopResponse(
                "stop-uuid-1",
                1,
                StopType.PICKUP,
                "Dealer Warehouse",
                "4251 Turin Dr, Bessemer, AL 35020",
                new BigDecimal("32.735"),
                new BigDecimal("-97.108"),
                "EDT",
                LocalDateTime.of(2026, 7, 17, 9, 30),
                LocalDateTime.of(2026, 7, 17, 10, 0),
                null,
                null,
                null,
                "CO 01660967",
                null,
                null,
                new BigDecimal("83.00"),
                new BigDecimal("13.00"),
                new BigDecimal("406717"));
        ManifestRouteResponse response = new ManifestRouteResponse(
                List.of(stop),
                new ManifestStartingPositionResponse(
                        "Prior stop, GA",
                        new BigDecimal("33.101"),
                        new BigDecimal("-87.99"),
                        "note",
                        new BigDecimal("74.00"),
                        new BigDecimal("174.00"),
                        new BigDecimal("406543")),
                "abc123",
                160934L,
                "7203.500s");
        when(this.manifestRouteService.findRoute(1000589L)).thenReturn(Optional.of(response));

        this.mockMvc
                .perform(get("/api/manifests/1000589/route").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.stops[0].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.startingPosition.address").value("Prior stop, GA"))
                .andExpect(jsonPath("$.encodedPolyline").value("abc123"))
                .andExpect(jsonPath("$.distanceMeters").value(160934));
    }

    // Regression test: a manifest whose stops Google couldn't route between (see ManifestRouteServiceImpl's javadoc)
    // still returns its stops/starting position with a 200, just with null route geometry fields, rather than 404ing
    // the whole response.
    @Test
    void route_found_withoutGoogleRoute_returnsStopsWithNullRouteGeometry() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        ManifestStopResponse stop = new ManifestStopResponse(
                "stop-uuid-1",
                1,
                StopType.PICKUP,
                "Dealer Warehouse",
                "4251 Turin Dr, Bessemer, AL 35020",
                new BigDecimal("32.735"),
                new BigDecimal("-97.108"),
                "EDT",
                LocalDateTime.of(2026, 7, 17, 9, 30),
                LocalDateTime.of(2026, 7, 17, 10, 0),
                null,
                null,
                null,
                "CO 01660967",
                null,
                null,
                new BigDecimal("83.00"),
                new BigDecimal("13.00"),
                new BigDecimal("406717"));
        ManifestRouteResponse response = new ManifestRouteResponse(List.of(stop), null, null, null, null);
        when(this.manifestRouteService.findRoute(1000589L)).thenReturn(Optional.of(response));

        this.mockMvc
                .perform(get("/api/manifests/1000589/route").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.encodedPolyline").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.distanceMeters").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void driverLocation_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.manifestDriverLocationService.findLiveLocation(1000589L)).thenReturn(Optional.empty());

        this.mockMvc
                .perform(
                        get("/api/manifests/1000589/driver-location").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void driverLocation_found_returnsLocation() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        ManifestDriverLocationResponse response = new ManifestDriverLocationResponse(
                new BigDecimal("30.4183333"),
                new BigDecimal("-89.1889962"),
                new BigDecimal("294.91"),
                LocalDateTime.of(2026, 7, 19, 2, 18, 7),
                "Long Beach, MS");
        when(this.manifestDriverLocationService.findLiveLocation(1000589L)).thenReturn(Optional.of(response));

        this.mockMvc
                .perform(
                        get("/api/manifests/1000589/driver-location").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(30.4183333))
                .andExpect(jsonPath("$.longitude").value(-89.1889962))
                .andExpect(jsonPath("$.formattedLocation").value("Long Beach, MS"));
    }

    @Test
    void eta_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.manifestEtaService.findEta(1000589L)).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/manifests/1000589/eta").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eta_found_returnsEta() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        ManifestEtaResponse response =
                new ManifestEtaResponse(5, new BigDecimal("552.86"), 567, LocalDateTime.of(2026, 7, 19, 2, 16, 0));
        when(this.manifestEtaService.findEta(1000589L)).thenReturn(Optional.of(response));

        this.mockMvc
                .perform(get("/api/manifests/1000589/eta").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopSequenceNumber").value(5))
                .andExpect(jsonPath("$.remainingMiles").value(552.86))
                .andExpect(jsonPath("$.remainingMinutes").value(567));
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
