package com.lava.web.oauth;

import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Handles provider-side OAuth failures (consent denied, state/PKCE mismatch, provider error) that Spring Security's own
 * oauth2Login filter catches before a principal is ever produced.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuthProperties oAuthProperties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("onAuthenticationFailure::{}", LogSanitizer.sanitize(exception.getMessage()));
        response.sendRedirect(this.oAuthProperties.failureRedirectUri());
    }
}
