package com.lava.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The CSRF token in a response body, for a frontend on another origin.
 *
 * <p>The double-submit that {@code csrf.spa()} sets up expects script to read the {@code XSRF-TOKEN} cookie and echo it
 * in {@code X-XSRF-TOKEN}. That works for a frontend served from this service's own origin, as npecloud's is. It cannot
 * work for one on a sibling origin - PlateTune's app on {@code platetune.com} calling {@code auth.platetune.com} -
 * since script can read only its own origin's cookies. That app fetches the token here instead, through CORS, keeps it
 * in memory and sends it as the header. The cookie itself still travels with the request, because the two hosts are the
 * same site, so the filter's comparison is unchanged: nothing is weakened for either frontend.
 *
 * <p>The raw token, deliberately. {@code csrf.spa()} exposes a BREACH-masked token as the {@code CsrfToken} request
 * attribute but compares a header value against the raw one, so returning the masked value would fail every check.
 */
@RestController
@RequestMapping("/api/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(HttpServletRequest request) {
        // CsrfFilter puts the unmasked token here. get() loads it, or generates one and has the repository set the
        // XSRF-TOKEN cookie on this response - so the cookie and the body always agree.
        CsrfToken token = ((DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName())).get();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }

    /** @param headerName - the header to send the token in, so a client need not hard-code {@code X-XSRF-TOKEN}. */
    public record CsrfTokenResponse(String headerName, String token) {}
}
