package com.lava.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.CorsProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.boot.autoconfigure.app.MfaProperties;
import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.configuration.SecurityConfiguration;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.model.mfa.TotpEnrollment;
import com.lava.model.mfa.TotpEnrollmentBuilder;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.MfaAuthorities;
import com.lava.security.oauth.GithubEmailBackfillOAuth2UserService;
import com.lava.service.AuthService;
import com.lava.service.JwtService;
import com.lava.service.MfaService;
import com.lava.web.AuthCookieFactory;
import com.lava.web.oauth.OAuthAuthenticationFailureHandler;
import com.lava.web.oauth.OAuthAuthenticationSuccessHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MfaController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties({
    CookieProperties.class,
    CorsProperties.class,
    JwtProperties.class,
    MfaProperties.class,
    OAuthProperties.class
})
@ActiveProfiles("test")
class MfaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthCookieFactory cookieFactory;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MfaService mfaService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private GithubEmailBackfillOAuth2UserService githubEmailBackfillOAuth2UserService;

    @MockitoBean
    private OAuthAuthenticationSuccessHandler oAuthAuthenticationSuccessHandler;

    @MockitoBean
    private OAuthAuthenticationFailureHandler oAuthAuthenticationFailureHandler;

    @Test
    void enroll_unauthenticated_returns401() throws Exception {
        this.mockMvc.perform(post("/api/auth/mfa/enroll").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void enroll_authenticatedNotEnrolled_returnsEnrollmentPayload() throws Exception {
        AuthUserPrincipal principal = principal(1L, Set.of());
        when(this.mfaService.startEnrollment(principal)).thenReturn(enrollment());

        this.mockMvc
                .perform(post("/api/auth/mfa/enroll").with(csrf()).with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaMethodId").value(10))
                .andExpect(jsonPath("$.secret").value("SECRET"))
                .andExpect(jsonPath("$.otpAuthUri").value("otpauth://totp/uri"))
                .andExpect(jsonPath("$.qrCodeDataUri").value("data:image/png;base64,x"));
    }

    @Test
    void enrollVerify_validRequest_returnsBackupCodes() throws Exception {
        AuthUserPrincipal principal = principal(1L, Set.of());
        when(this.mfaService.confirmEnrollment(eq(principal), eq(10L), eq("123456")))
                .thenReturn(List.of("CODE1", "CODE2"));

        this.mockMvc
                .perform(post("/api/auth/mfa/enroll/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaMethodId\":10,\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes.length()").value(2));
    }

    @Test
    void enrollVerify_blankCode_returns400() throws Exception {
        AuthUserPrincipal principal = principal(1L, Set.of());

        this.mockMvc
                .perform(post("/api/auth/mfa/enroll/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaMethodId\":10,\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_passwordOnlyFactorPrincipal_isReachableAndSetsAccessTokenCookie() throws Exception {
        // MFA-enrolled principal that has NOT yet satisfied the TOTP factor - this endpoint must
        // remain reachable specifically for this principal, unlike every other protected route.
        AuthUserPrincipal principal = principal(
                1L, Set.of(new SimpleGrantedAuthority(MfaAuthorities.MFA_ENROLLED_AUTHORITY), passwordFactor()));
        when(this.authService.completeMfaVerification(eq(principal), eq("raw-refresh"), eq("123456")))
                .thenReturn("new-access-token");
        when(this.cookieFactory.accessTokenCookie("new-access-token"))
                .thenReturn(
                        ResponseCookie.from("ACCESS_TOKEN", "new-access-token").build());

        this.mockMvc
                .perform(post("/api/auth/mfa/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN_COOKIE, "raw-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void verify_invalidCode_returns401() throws Exception {
        AuthUserPrincipal principal = principal(
                1L, Set.of(new SimpleGrantedAuthority(MfaAuthorities.MFA_ENROLLED_AUTHORITY), passwordFactor()));
        when(this.authService.completeMfaVerification(eq(principal), any(), eq("000000")))
                .thenThrow(new InvalidTotpCodeException());

        this.mockMvc
                .perform(post("/api/auth/mfa/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN_COOKIE, "raw-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_noRefreshTokenCookie_returns400() throws Exception {
        AuthUserPrincipal principal = principal(
                1L, Set.of(new SimpleGrantedAuthority(MfaAuthorities.MFA_ENROLLED_AUTHORITY), passwordFactor()));

        this.mockMvc
                .perform(post("/api/auth/mfa/verify")
                        .with(csrf())
                        .with(authentication(authToken(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    private static GrantedAuthority passwordFactor() {
        return FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    private static Authentication authToken(AuthUserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static AuthUserPrincipal principal(Long id, Set<GrantedAuthority> authorities) {
        return AuthUserPrincipal.builder()
                .userId(id)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(authorities)
                .build();
    }

    private static TotpEnrollment enrollment() {
        return TotpEnrollmentBuilder.builder()
                .mfaMethodId(10L)
                .secret("SECRET")
                .otpAuthUri("otpauth://totp/uri")
                .qrCodeDataUri("data:image/png;base64,x")
                .build();
    }
}
