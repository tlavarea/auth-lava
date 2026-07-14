package com.lava.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.JwtService;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JwksController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(CorsProperties.class)
@ActiveProfiles("test")
class JwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService;

    @MockitoBean
    private OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler;

    @MockitoBean
    private OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler;

    @Test
    void jwks_unauthenticated_returnsPublicKeySet() throws Exception {
        KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
        when(this.jwtService.getPublicKey()).thenReturn(keyPair.getPublic());
        when(this.jwtService.getKeyId()).thenReturn("auth-lava-key-1");

        this.mockMvc
                .perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("auth-lava-key-1"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
