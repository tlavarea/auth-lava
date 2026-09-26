package com.lava.security.oauth;

import com.lava.boot.autoconfigure.app.CookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Holds the in-flight {@link OAuth2AuthorizationRequest} in a cookie rather than an {@code HttpSession}.
 *
 * <p>Two reasons, and the second is the one that forces it.
 *
 * <p><b>Apple's callback is cross-site.</b> Asking Apple for {@code scope=name email} obliges
 * {@code response_mode=form_post}, so the browser returns from {@code appleid.apple.com} with a {@code POST} rather
 * than a top-level {@code GET} navigation. A {@code SameSite=Lax} cookie is sent on the second and not on the first, so
 * Spring's default {@code HttpSessionOAuth2AuthorizationRequestRepository} finds nothing and the login dies as
 * {@code authorization_request_not_found} — the state, the PKCE verifier and the nonce having all been left behind a
 * cookie the browser declined to send. Nothing about the provider configuration fixes that; the store has to change.
 *
 * <p><b>The filter chain already claims to be stateless.</b> {@code SessionCreationPolicy.STATELESS} governs the
 * security context, not {@code request.getSession()}, which the session-backed repository calls directly — so every
 * Google and GitHub sign-in has quietly been minting a {@code JSESSIONID} the rest of the application does not use.
 * This makes the declared policy true.
 *
 * <h2>Why the cookie may be {@code SameSite=None}</h2>
 *
 * It has to be, for the POST above to carry it. That is safe here because this cookie is not a credential: it is one
 * half of the CSRF check, useless without a {@code state} parameter in the same request that matches the {@code state}
 * inside it. It is {@code HttpOnly} and {@code Secure}, it lives for {@link #TTL}, and it is deleted the moment the
 * callback consumes it.
 *
 * <h2>Why the contents are re-read field by field</h2>
 *
 * This cookie arrives on an endpoint that by definition has not authenticated anybody, which makes its contents
 * attacker-supplied input. Java serialization would therefore be a deserialization gadget on an open endpoint, and
 * Jackson's {@code activateDefaultTyping} is the same hole wearing JSON. So the payload is a flat record of strings,
 * and what comes back is used to <i>rebuild</i> an authorization request rather than to instantiate whatever type the
 * cookie asked for. The worst a forged cookie can do is describe a valid-looking request whose {@code state} then fails
 * to match.
 */
@Slf4j
@RequiredArgsConstructor
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "OAUTH2_AUTHORIZATION_REQUEST";

    /**
     * Long enough for a first-time Apple sign-in — Apple ID, two-factor, and the one-off "share or hide my email"
     * consent — and short enough that an abandoned attempt is not still lying around an hour later.
     */
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * Browsers drop a {@code Set-Cookie} over roughly 4096 bytes, silently, which would surface as
     * {@code authorization_request_not_found} at the callback and send anyone debugging it to the provider. A live
     * request is ~700 bytes, so this is a tripwire for a future scope or parameter, not a limit anyone is near.
     */
    private static final int MAX_COOKIE_BYTES = 4096;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CookieProperties cookieProperties;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String value = readCookie(request);
        if (StringUtils.isBlank(value)) {
            return null;
        }

        try {
            return JSON.readValue(Base64.getUrlDecoder().decode(value), SerializedRequest.class)
                    .toAuthorizationRequest();
        } catch (RuntimeException e) {
            // A truncated, tampered-with or stale-format cookie is indistinguishable from no cookie at all as far as
            // the login is concerned, and throwing here would turn it into a 500 on a public endpoint. Logged at debug
            // rather than warn: an expired cookie replayed by a bookmarked callback is routine, not an incident.
            log.debug("Discarding an unreadable OAuth2 authorization request cookie", e);
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        // Spring calls save(null, ..) to mean delete. Honouring it here rather than treating it as an error is part of
        // the contract: OAuth2AuthorizationCodeGrantFilter relies on it to clear the request it just consumed.
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String value = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(JSON.writeValueAsBytes(SerializedRequest.of(authorizationRequest)));

        ResponseCookie cookie = cookie(value, TTL);
        String header = cookie.toString();
        if (header.length() > MAX_COOKIE_BYTES) {
            throw new IllegalStateException(
                    "OAuth2 authorization request cookie is %d bytes, over the ~%d a browser will keep"
                            .formatted(header.length(), MAX_COOKIE_BYTES));
        }

        response.addHeader(HttpHeaders.SET_COOKIE, header);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        // Cleared unconditionally, not only when one was found: a cookie this method could not parse is exactly the one
        // that should not survive to be re-read on the next attempt.
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
        return authorizationRequest;
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                // "/" is forced: the cookie is written on /oauth2/authorization/** and read back on
                // /login/oauth2/code/**, which share no prefix to narrow it to.
                .path("/")
                .maxAge(maxAge);

        if (StringUtils.isNotBlank(cookieProperties.domain())) {
            builder.domain(cookieProperties.domain());
        }

        return builder.build();
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * The cookie's payload: every field of an {@link OAuth2AuthorizationRequest} that has to survive the round trip,
     * flattened to strings.
     *
     * <p>{@code additionalParameters} and {@code attributes} are declared {@code Map<String, Object>} but everything
     * Spring puts in them is a string — the registration id, the PKCE challenge and verifier, the OIDC nonce. Narrowing
     * them to {@code Map<String, String>} is what keeps the deserializer from ever being asked to decide a type, so a
     * non-string value is refused at save time rather than quietly coming back as its {@code toString()}.
     */
    record SerializedRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, String> additionalParameters,
            Map<String, String> attributes,
            String authorizationRequestUri) {

        static SerializedRequest of(OAuth2AuthorizationRequest request) {
            return new SerializedRequest(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    request.getState(),
                    flatten(request.getAdditionalParameters(), "additionalParameters"),
                    flatten(request.getAttributes(), "attributes"),
                    request.getAuthorizationRequestUri());
        }

        OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes == null ? Set.of() : new LinkedHashSet<>(scopes))
                    .state(state)
                    .additionalParameters(widen(additionalParameters))
                    .attributes(widen(attributes))
                    // Restored rather than recomputed. The builder would happily rebuild it from the fields above, but
                    // a URI assembled twice by two versions of the same builder is a URI that can differ, and this one
                    // is the string the browser was actually sent to.
                    .authorizationRequestUri(authorizationRequestUri)
                    .build();
        }

        private static Map<String, String> flatten(Map<String, Object> source, String what) {
            Map<String, String> flattened = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (value != null && !(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "OAuth2 authorization request %s[%s] is a %s; only strings survive the cookie"
                                    .formatted(what, key, value.getClass().getName()));
                }
                flattened.put(key, (String) value);
            });
            return flattened;
        }

        private static Map<String, Object> widen(Map<String, String> source) {
            return source == null ? Map.of() : new LinkedHashMap<>(source);
        }
    }
}
