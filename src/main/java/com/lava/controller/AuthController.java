package com.lava.controller;

import com.lava.logging.LogSanitizer;
import com.lava.model.web.request.LoginRequest;
import com.lava.model.web.request.RegisterRequest;
import com.lava.model.web.response.UserResponse;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("register::request: {}", LogSanitizer.sanitize(request));
        this.authService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        log.info("login::request: {}", LogSanitizer.sanitize(request));
        AuthUserPrincipal principal =
                this.authService.login(request.email(), request.password(), httpRequest, httpResponse);
        return ResponseEntity.ok(UserResponse.from(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("logout");
        this.authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
