package com.lava.web.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.exception.InvalidOAuthUserStateException;
import com.lava.model.auth.TokenPair;
import com.lava.model.auth.TokenPairBuilder;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.OAuthAuthenticationService;
import com.lava.web.AuthCookieFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

@ExtendWith(MockitoExtension.class)
class OAuthAuthenticationSuccessHandlerTest {

    @Mock
    private AuthCookieFactory cookieFactory;

    @Mock
    private OAuthAuthenticationService oAuthAuthenticationService;

    private OAuthAuthenticationSuccessHandler handler;

    @BeforeEach
    void setUp() {
        OAuthProperties properties =
                new OAuthProperties("http://localhost:4200/", "http://localhost:4200/login?error=oauth");
        this.handler =
                new OAuthAuthenticationSuccessHandler(this.cookieFactory, this.oAuthAuthenticationService, properties);
    }

    @Test
    void onAuthenticationSuccess_verifiedIdentity_setsCookiesAndRedirectsToSuccessUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenPair pair = tokenPair();
        when(this.oAuthAuthenticationService.authenticate(any())).thenReturn(pair);
        when(this.cookieFactory.accessTokenCookie("access")).thenReturn(cookie("ACCESS_TOKEN", "access"));
        when(this.cookieFactory.refreshTokenCookie("refresh")).thenReturn(cookie("REFRESH_TOKEN", "refresh"));

        this.handler.onAuthenticationSuccess(request, response, googleToken(true));

        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/");
    }

    @Test
    void onAuthenticationSuccess_unverifiedEmail_redirectsToFailureUriWithoutSettingCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        this.handler.onAuthenticationSuccess(request, response, googleTokenWithoutEmail());

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/login?error=oauth");
    }

    @Test
    void onAuthenticationSuccess_invalidUserState_redirectsToFailureUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(this.oAuthAuthenticationService.authenticate(any())).thenThrow(new InvalidOAuthUserStateException());

        this.handler.onAuthenticationSuccess(request, response, googleToken(true));

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/login?error=oauth");
    }

    private static TokenPair tokenPair() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash(null)
                .status("active")
                .emailVerified(true)
                .authorities(Set.of())
                .build();

        return TokenPairBuilder.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .expiresInSeconds(900L)
                .principal(principal)
                .build();
    }

    private static org.springframework.http.ResponseCookie cookie(String name, String value) {
        return org.springframework.http.ResponseCookie.from(name, value).build();
    }

    private static OAuth2AuthenticationToken googleToken(boolean verified) {
        Map<String, Object> claims = Map.of("sub", "g-123", "email", "user@example.com", "email_verified", verified);
        OidcIdToken idToken =
                new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }

    private static OAuth2AuthenticationToken googleTokenWithoutEmail() {
        Map<String, Object> claims = Map.of("sub", "g-123");
        OidcIdToken idToken =
                new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        DefaultOidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }
}
