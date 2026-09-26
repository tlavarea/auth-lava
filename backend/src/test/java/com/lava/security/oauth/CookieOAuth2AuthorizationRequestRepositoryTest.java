package com.lava.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lava.boot.autoconfigure.app.CookieProperties;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(new CookieProperties(null));

    @Test
    @DisplayName("round-trips every field the callback needs, including the PKCE verifier")
    void roundTripsTheRequest() {
        OAuth2AuthorizationRequest saved = appleRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(saved, new MockHttpServletRequest(), response);
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(requestCarrying(response));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo(saved.getState());
        assertThat(loaded.getClientId()).isEqualTo(saved.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(saved.getRedirectUri());
        assertThat(loaded.getScopes()).isEqualTo(saved.getScopes());
        assertThat(loaded.getAuthorizationUri()).isEqualTo(saved.getAuthorizationUri());
        assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(saved.getAuthorizationRequestUri());
        // The two that actually fail the login when they go missing.
        assertThat(loaded.<String>getAttribute("registration_id")).isEqualTo("apple");
        assertThat(loaded.<String>getAttribute("code_verifier")).isEqualTo("a-pkce-code-verifier");
        assertThat(loaded.getAdditionalParameters()).isEqualTo(saved.getAdditionalParameters());
    }

    @Test
    @DisplayName("sets SameSite=None; Secure, without which Apple's cross-site POST carries nothing")
    void cookieIsSentOnACrossSitePost() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(appleRequest(), new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("SameSite=None").contains("Secure").contains("HttpOnly");
    }

    @Test
    @DisplayName("scopes the cookie to the configured domain, so it spans auth. and the app")
    void appliesTheCookieDomain() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CookieOAuth2AuthorizationRequestRepository(new CookieProperties(".platetune.com"))
                .saveAuthorizationRequest(appleRequest(), new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Domain=.platetune.com");
    }

    @Test
    @DisplayName("returns null rather than throwing when there is no cookie at all")
    void noCookieIsNotAnError() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest()))
                .isNull();
    }

    @Test
    @DisplayName("treats a tampered cookie as absent, not as a 500 on a public endpoint")
    void garbageCookieIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OAUTH2_AUTHORIZATION_REQUEST", "not-base64-and-not-json"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    @DisplayName("remove returns the request and clears the cookie in the same response")
    void removeConsumesTheCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(appleRequest(), new MockHttpServletRequest(), saveResponse);

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(requestCarrying(saveResponse), removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("clears the cookie even when it could not be read, so a bad one cannot persist")
    void removeClearsAnUnreadableCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OAUTH2_AUTHORIZATION_REQUEST", "rubbish"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(repository.removeAuthorizationRequest(request, response)).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("save(null) means delete - the contract the code-grant filter relies on")
    void savingNullDeletes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    @DisplayName("refuses a non-string attribute at save time rather than mangling it on the way back")
    void refusesNonStringAttributes() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .clientId("com.platetune.web")
                .state("state")
                .attributes(Map.of("registration_id", "apple", "retries", 3))
                .build();

        assertThatThrownBy(() -> repository.saveAuthorizationRequest(
                        request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retries");
    }

    /** As close to what DefaultOAuth2AuthorizationRequestResolver builds for Apple as matters here. */
    private OAuth2AuthorizationRequest appleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .clientId("com.platetune.web")
                .redirectUri("https://auth.platetune.com/login/oauth2/code/apple")
                .scopes(Set.of("name", "email"))
                .state("Ry9xQ0pZa1hn")
                .additionalParameters(Map.of(
                        "response_mode", "form_post",
                        "code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                        "code_challenge_method", "S256",
                        "nonce", "a-nonce"))
                .attributes(Map.of("registration_id", "apple", "code_verifier", "a-pkce-code-verifier"))
                .build();
    }

    private MockHttpServletRequest requestCarrying(MockHttpServletResponse response) {
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OAUTH2_AUTHORIZATION_REQUEST", value));
        return request;
    }
}
