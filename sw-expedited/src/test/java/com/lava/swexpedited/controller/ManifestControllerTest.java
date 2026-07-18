package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import com.lava.swexpedited.manifest.ManifestRouteResponse;
import com.lava.swexpedited.service.ManifestRouteService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
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
        ManifestRouteResponse response = new ManifestRouteResponse(
                new BigDecimal("32.735"),
                new BigDecimal("-97.108"),
                new BigDecimal("33.101"),
                new BigDecimal("-87.99"),
                "abc123",
                160934L,
                "7203.500s");
        when(this.manifestRouteService.findRoute(1000589L)).thenReturn(Optional.of(response));

        this.mockMvc
                .perform(get("/api/manifests/1000589/route").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originLatitude").value(32.735))
                .andExpect(jsonPath("$.destinationLatitude").value(33.101))
                .andExpect(jsonPath("$.encodedPolyline").value("abc123"))
                .andExpect(jsonPath("$.distanceMeters").value(160934));
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
