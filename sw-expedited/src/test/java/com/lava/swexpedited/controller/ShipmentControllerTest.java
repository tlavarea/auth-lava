package com.lava.swexpedited.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.service.ShipmentService;
import com.lava.swexpedited.shipment.ShipmentDetailResponse;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ShipmentController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class ShipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShipmentService shipmentService;

    @Test
    void shipments_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/shipments")).andExpect(status().isUnauthorized());
    }

    @Test
    void shipments_withValidAccessTokenCookie_returnsListing() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject("42")
                .claim("authorities", List.of("ROLE_MEMBER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.shipmentService.findAll())
                .thenReturn(List.of(new ShipmentListingRow(
                        1284311010L,
                        "Open",
                        null,
                        "KLFV160850003",
                        "FAK",
                        "36",
                        "KLFV",
                        "774900240, KIRTLAND AFB,NM",
                        "773466240, CANNON AFB,NM",
                        "AF2",
                        1,
                        0,
                        null,
                        null,
                        null,
                        false)));

        this.mockMvc
                .perform(get("/api/shipments").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offerId").value(1284311010))
                .andExpect(jsonPath("$[0].status").value("Open"))
                .andExpect(jsonPath("$[0].shipmentId").value("KLFV160850003"));
    }

    @Test
    void shipment_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/shipments/1284311010")).andExpect(status().isUnauthorized());
    }

    @Test
    void shipment_notFound_returns404() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        when(this.shipmentService.findDetail(1284311010L)).thenReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/shipments/1284311010").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shipment_found_returnsDetail() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        ShipmentListingRow listing = new ShipmentListingRow(
                1284311010L,
                "Open",
                null,
                "KLFV160850003",
                "FAK",
                "36",
                "KLFV",
                "774900240, KIRTLAND AFB,NM",
                "773466240, CANNON AFB,NM",
                "AF2",
                1,
                0,
                null,
                null,
                null,
                false);
        when(this.shipmentService.findDetail(1284311010L))
                .thenReturn(Optional.of(new ShipmentDetailResponse(
                        listing, null, null, null, "SWJJ", null, null, null, null, null, "{\"bid\":{}}", null, null)));

        this.mockMvc
                .perform(get("/api/shipments/1284311010").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.offerId").value(1284311010))
                .andExpect(jsonPath("$.scac").value("SWJJ"))
                .andExpect(jsonPath("$.rawResponse.bid").exists());
    }

    @Test
    void respondToOffer_withoutCookie_returns401() throws Exception {
        this.mockMvc
                .perform(post("/api/shipments/1284311010/respond")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPT\",\"conveyancesAvailable\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respondToOffer_notYetImplemented_returns501() throws Exception {
        Jwt jwt = authenticatedJwt();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);
        doThrow(new ResponseStatusException(
                        HttpStatus.NOT_IMPLEMENTED, "GFM offer-response submission isn't wired up yet"))
                .when(this.shipmentService)
                .respondToOffer(anyLong(), any());

        this.mockMvc
                .perform(post("/api/shipments/1284311010/respond")
                        .with(csrf())
                        .cookie(new Cookie("ACCESS_TOKEN", "token-value"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"ACCEPT\",\"conveyancesAvailable\":1}"))
                .andExpect(status().isNotImplemented());
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
