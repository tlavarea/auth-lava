package com.lava.web.oauth;

import com.lava.boot.autoconfigure.app.OAuthProperties;
import com.lava.exception.InvalidOAuthUserStateException;
import com.lava.exception.UnverifiedOAuthEmailException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.TokenPair;
import com.lava.security.oauth.OAuthIdentityMapper;
import com.lava.service.OAuthAuthenticationService;
import com.lava.web.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Bridges a successful Google/GitHub login into the same JWT + refresh-token cookie pair the password login flow
 * issues, so every other endpoint (/api/auth/me, /refresh, /logout, JwtAuthenticationFilter) needs no knowledge of how
 * the session started.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthCookieFactory cookieFactory;
    private final OAuthAuthenticationService oAuthAuthenticationService;
    private final OAuthProperties oAuthProperties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        try {
            TokenPair pair = OAuthIdentityMapper.from((OAuth2AuthenticationToken) authentication)
                    .map(this.oAuthAuthenticationService::authenticate)
                    .orElseThrow(UnverifiedOAuthEmailException::new);

            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    this.cookieFactory.accessTokenCookie(pair.accessToken()).toString());
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    this.cookieFactory.refreshTokenCookie(pair.refreshToken()).toString());
            log.info(
                    "onAuthenticationSuccess::success::user: {}",
                    LogSanitizer.sanitize(pair.principal().getUsername()));
            response.sendRedirect(this.oAuthProperties.successRedirectUri());
        } catch (UnverifiedOAuthEmailException | InvalidOAuthUserStateException e) {
            log.error("onAuthenticationSuccess::rejected: {}", LogSanitizer.sanitize(ExceptionUtils.getMessage(e)), e);
            response.sendRedirect(this.oAuthProperties.failureRedirectUri());
        }
    }
}
