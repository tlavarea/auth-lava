package com.lava.controller;

import com.lava.logging.LogSanitizer;
import com.lava.model.web.request.LoginRequest;
import com.lava.model.web.request.LogoutRequest;
import com.lava.model.web.request.RefreshRequest;
import com.lava.model.web.request.RegisterRequest;
import com.lava.model.web.response.TokenResponse;
import com.lava.model.web.response.UserResponse;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.AuthService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("login::request: {}", LogSanitizer.sanitize(request));
        return ResponseEntity.ok(TokenResponse.from(this.authService.login(request.email(), request.password())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @RequestBody(required = false) LogoutRequest request) {
        log.info("logout::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        this.authService.logout(principal, Optional.ofNullable(request).map(LogoutRequest::refreshToken));
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
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("refresh::request received");
        return ResponseEntity.ok(TokenResponse.from(this.authService.refresh(request.refreshToken())));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("register::request: {}", LogSanitizer.sanitize(request));
        this.authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
