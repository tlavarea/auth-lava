package com.lava.controller;

import com.lava.exception.InvalidRefreshTokenException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.TokenPair;
import com.lava.model.web.request.LoginRequest;
import com.lava.model.web.request.LogoutRequest;
import com.lava.model.web.response.UserResponse;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.AuthService;
import com.lava.web.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("login::request: {}", LogSanitizer.sanitize(request));
        TokenPair pair = this.authService.login(request.email(), request.password());
        this.setAuthCookies(response, pair);
        return ResponseEntity.ok(UserResponse.from(pair.principal(), pair.mfaEnrolled()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletResponse response) {
        boolean allDevices = request != null && request.allDevices();
        log.info("logout::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        this.authService.logout(principal, allDevices ? Optional.empty() : Optional.ofNullable(refreshTokenCookie));
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                this.cookieFactory.clearedAccessTokenCookie().toString());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                this.cookieFactory.clearedRefreshTokenCookie().toString());
        return ResponseEntity.noContent().build();
    }

    // Not permitAll: the SPA calls this on startup to learn whether it's still authenticated
    // (a stored access token is still valid) without forcing a fresh login. Unauthenticated
    // callers fall through to the standard 401 entry point automatically. Either way, the
    // response carries a fresh XSRF-TOKEN cookie (the CSRF filter issues one on every request),
    // which is all the SPA needs before it can POST to /login or /register.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok(UserResponse.from(principal));
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserResponse> refresh(
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            HttpServletResponse response) {
        if (refreshTokenCookie == null) {
            throw new InvalidRefreshTokenException();
        }

        log.info("refresh::request received");
        TokenPair pair = this.authService.refresh(refreshTokenCookie);
        this.setAuthCookies(response, pair);
        return ResponseEntity.ok(UserResponse.from(pair.principal(), pair.mfaEnrolled()));
    }

    /**
     * Sets the access token and refresh token cookies on the response.
     *
     * @param response - the {@link HttpServletResponse}.
     * @param pair - the {@link TokenPair} object which contains both JWT's.
     */
    private void setAuthCookies(HttpServletResponse response, TokenPair pair) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                this.cookieFactory.accessTokenCookie(pair.accessToken()).toString());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                this.cookieFactory.refreshTokenCookie(pair.refreshToken()).toString());
    }
}
