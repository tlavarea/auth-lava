package com.lava.controller;

import com.lava.logging.LogSanitizer;
import com.lava.model.web.request.EmailChangeStartRequest;
import com.lava.model.web.request.EmailChangeVerifyRequest;
import com.lava.model.web.response.UserResponse;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.EmailChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
@Slf4j
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    @PostMapping("/change")
    public ResponseEntity<Void> start(
            @AuthenticationPrincipal AuthUserPrincipal principal, @Valid @RequestBody EmailChangeStartRequest request) {
        log.info("start::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        this.emailChangeService.start(principal.getUserId(), request.newEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change/verify")
    public ResponseEntity<UserResponse> verify(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @Valid @RequestBody EmailChangeVerifyRequest request) {
        String newEmail = this.emailChangeService.confirm(principal.getUserId(), request.code());
        log.info("verify::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        return ResponseEntity.ok(
                UserResponse.from(principal).withEmail(newEmail).withEmailVerified(true));
    }
}
