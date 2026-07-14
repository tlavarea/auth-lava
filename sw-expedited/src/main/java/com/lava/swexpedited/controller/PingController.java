package com.lava.swexpedited.controller;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the auth-lava trust boundary works end-to-end: reachable only with a valid ACCESS_TOKEN cookie verified
 * against auth-lava's JWKS. Replace with real endpoints once feature work starts here.
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
                "subject", jwt.getSubject(),
                "email", jwt.getClaimAsString("email"),
                "authorities", jwt.getClaimAsStringList("authorities"));
    }
}
