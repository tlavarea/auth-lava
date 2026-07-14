package com.lava.swexpedited.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.swexpedited.boot.autoconfigure.app.CorsProperties;
import com.lava.swexpedited.configuration.SecurityConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void ping_withoutCookie_returns401() throws Exception {
        this.mockMvc.perform(get("/api/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void ping_withValidAccessTokenCookie_returnsPrincipalClaims() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject("42")
                .claim("email", "user@example.com")
                .claim("authorities", List.of("ROLE_MEMBER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(this.jwtDecoder.decode("token-value")).thenReturn(jwt);

        this.mockMvc
                .perform(get("/api/ping").cookie(new Cookie("ACCESS_TOKEN", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("42"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_MEMBER"));
    }
}
