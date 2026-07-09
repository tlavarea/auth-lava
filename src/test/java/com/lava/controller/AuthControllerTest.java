package com.lava.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.model.auth.TokenPair;
import com.lava.model.auth.TokenPairBuilder;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.AuthService;
import com.lava.service.JwtService;
import com.lava.web.AuthCookieFactory;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({CookieProperties.class, CorsProperties.class, JwtProperties.class, OAuthProperties.class
})
@ActiveProfiles("test")
class AuthControllerTest {

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
    void login_validCredentials_returnsUserAndSetsCookies() throws Exception {
        this.stubCookies();
        AuthUserPrincipal principal = principal(1L);
        when(this.authService.login("user@example.com", "password")).thenReturn(tokenPair(principal));

        this.mockMvc
                .perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.iterableWithSize(2)));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(this.authService.login(any(), any())).thenThrow(new BadCredentialsException("bad"));

        this.mockMvc
                .perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void register_success_returns201() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        verify(this.authService).register("new@example.com", "password123");
    }

    @Test
    void register_emailAlreadyRegistered_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new EmailAlreadyRegisteredException("dup@example.com"))
                .when(this.authService)
                .register(eq("dup@example.com"), any());

        this.mockMvc
                .perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        this.mockMvc
                .perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_authenticated_returnsCurrentUser() throws Exception {
        AuthUserPrincipal principal = principal(7L);

        this.mockMvc
                .perform(get("/api/auth/me").with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void me_notEnrolledInMfa_reachableOnPasswordFactorAlone() throws Exception {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(7L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FACTOR_PASSWORD")))
                .build();

        this.mockMvc
                .perform(get("/api/auth/me").with(authentication(authToken(principal))))
                .andExpect(status().isOk());
    }

    @Test
    void me_mfaEnrolledWithoutTotpFactor_returns403() throws Exception {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(7L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("MFA_ENROLLED"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FACTOR_PASSWORD")))
                .build();

        this.mockMvc
                .perform(get("/api/auth/me").with(authentication(authToken(principal))))
                .andExpect(status().isForbidden());
    }

    @Test
    void me_mfaEnrolledWithBothFactors_returnsOk() throws Exception {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(7L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("MFA_ENROLLED"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FACTOR_PASSWORD"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("FACTOR_TOTP")))
                .build();

        this.mockMvc
                .perform(get("/api/auth/me").with(authentication(authToken(principal))))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_noCookie_returns401() throws Exception {
        this.mockMvc.perform(post("/api/auth/refresh").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_returnsRotatedTokens() throws Exception {
        this.stubCookies();
        AuthUserPrincipal principal = principal(1L);
        when(this.authService.refresh("raw-refresh")).thenReturn(tokenPair(principal));

        this.mockMvc
                .perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie(AuthCookieFactory.REFRESH_TOKEN_COOKIE, "raw-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(this.authService.refresh("bad-refresh")).thenThrow(new InvalidRefreshTokenException());

        this.mockMvc
                .perform(post("/api/auth/refresh")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie(AuthCookieFactory.REFRESH_TOKEN_COOKIE, "bad-refresh")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_allDevices_revokesAllSessions() throws Exception {
        this.stubClearedCookies();
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allDevices\":true}"))
                .andExpect(status().isNoContent());

        verify(this.authService).logout(eq(principal), eq(Optional.empty()));
    }

    @Test
    void logout_singleDeviceWithCookie_revokesOnlyThatToken() throws Exception {
        this.stubClearedCookies();
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .cookie(new jakarta.servlet.http.Cookie(AuthCookieFactory.REFRESH_TOKEN_COOKIE, "raw-refresh")))
                .andExpect(status().isNoContent());

        verify(this.authService).logout(eq(principal), eq(Optional.of("raw-refresh")));
    }

    @Test
    void logout_noBodyNoCookie_revokesAllSessions() throws Exception {
        this.stubClearedCookies();
        AuthUserPrincipal principal = principal(1L);

        this.mockMvc
                .perform(post("/api/auth/logout").with(csrf()).with(authentication(authToken(principal))))
                .andExpect(status().isNoContent());

        verify(this.authService).logout(eq(principal), eq(Optional.empty()));
    }

    private void stubCookies() {
        when(this.cookieFactory.accessTokenCookie(any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("ACCESS_TOKEN", "a")
                        .build());
        when(this.cookieFactory.refreshTokenCookie(any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("REFRESH_TOKEN", "r")
                        .build());
    }

    private void stubClearedCookies() {
        when(this.cookieFactory.clearedAccessTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("ACCESS_TOKEN", "")
                        .build());
        when(this.cookieFactory.clearedRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("REFRESH_TOKEN", "")
                        .build());
    }

    private static Authentication authToken(AuthUserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static AuthUserPrincipal principal(Long id) {
        return AuthUserPrincipal.builder()
                .userId(id)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of())
                .build();
    }

    private static TokenPair tokenPair(AuthUserPrincipal principal) {
        return TokenPairBuilder.builder()
                .accessToken("access")
                .refreshToken("raw-refresh")
                .expiresInSeconds(900L)
                .principal(principal)
                .build();
    }
}
