package com.lava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.AuthService;
import com.lava.service.JwtService;
import com.lava.web.AuthCookieFactory;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The token from {@code /api/auth/csrf} has to pass the real CSRF filter when sent back as the header - which is the
 * whole point of the endpoint, and exactly what returning the masked request attribute would break. {@code /refresh} is
 * the probe: permitted without authentication and CSRF-protected, so 403 means the token was refused and anything else
 * means it was accepted.
 */
@WebMvcTest({CsrfController.class, AuthController.class})
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class, JwtProperties.class, OAuthProperties.class
})
@ActiveProfiles("test")
class CsrfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieFactory cookieFactory;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService;

    @MockitoBean
    private OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler;

    @MockitoBean
    private OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler;

    @Test
    void csrf_unauthenticated_returnsTheRawTokenAndSetsTheMatchingCookie() throws Exception {
        MvcResult result = this.mockMvc
                .perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotBlank();
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).as("body and cookie must agree").isEqualTo(token);
    }

    @Test
    void csrf_withAnExistingCookie_returnsThatSameToken() throws Exception {
        MvcResult result = this.mockMvc
                .perform(get("/api/auth/csrf").cookie(new Cookie("XSRF-TOKEN", "already-issued")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.token"))
                .isEqualTo("already-issued");
    }

    @Test
    void tokenFromTheEndpoint_sentAsTheHeader_passesTheCsrfFilter() throws Exception {
        MvcResult issued = this.mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String token = JsonPath.read(issued.getResponse().getContentAsString(), "$.token");
        Cookie cookie = issued.getResponse().getCookie("XSRF-TOKEN");

        // No refresh cookie, so the controller answers 401 - which it can only do if the CSRF filter let it through.
        this.mockMvc
                .perform(post("/api/auth/refresh").cookie(cookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookieWithoutTheHeader_isStillRefused() throws Exception {
        Cookie cookie = this.mockMvc
                .perform(get("/api/auth/csrf"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        this.mockMvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test
    void aHeaderThatDoesNotMatchTheCookie_isRefused() throws Exception {
        Cookie cookie = this.mockMvc
                .perform(get("/api/auth/csrf"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        this.mockMvc
                .perform(post("/api/auth/refresh").cookie(cookie).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
    }
}
