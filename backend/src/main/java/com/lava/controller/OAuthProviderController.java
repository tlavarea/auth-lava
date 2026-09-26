package com.lava.controller;

import com.lava.security.oauth.EnabledOAuthProviders;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the SPA which OAuth buttons to render.
 *
 * <p>Providers differ per deployment (see application.yaml), so a frontend that hardcodes its own list drifts the first
 * time a deployment's profiles change — showing a button that 500s, or hiding one that works. Reading it from the
 * server keeps one source of truth.
 *
 * <p>Deliberately unauthenticated: it is needed to render the login page, and it reveals only which public
 * {@code /oauth2/authorization/<id>} endpoints already exist.
 */
@RestController
@RequiredArgsConstructor
public class OAuthProviderController {

    private final EnabledOAuthProviders enabledOAuthProviders;

    @GetMapping("/api/auth/providers")
    public Map<String, List<String>> providers() {
        return Map.of("providers", enabledOAuthProviders.registrationIds());
    }
}
