package com.lava.controller;

import com.lava.logging.LogSanitizer;
import com.lava.model.web.request.CompleteRegistrationRequest;
import com.lava.model.web.request.StartRegistrationRequest;
import com.lava.model.web.request.VerifyRegistrationCodeRequest;
import com.lava.model.web.response.RegistrationTokenResponse;
import com.lava.model.web.response.RegistrationTokenResponseBuilder;
import com.lava.service.RegistrationService;
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
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/start")
    public ResponseEntity<Void> start(@Valid @RequestBody StartRegistrationRequest request) {
        log.info("start::request: {}", LogSanitizer.sanitize(request.email()));
        this.registrationService.start(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-code")
    public ResponseEntity<RegistrationTokenResponse> verifyCode(
            @Valid @RequestBody VerifyRegistrationCodeRequest request) {
        log.info("verifyCode::request: {}", LogSanitizer.sanitize(request.email()));
        String registrationToken = this.registrationService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok(RegistrationTokenResponseBuilder.builder()
                .registrationToken(registrationToken)
                .build());
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody CompleteRegistrationRequest request) {
        log.info("complete::request received");
        this.registrationService.complete(request.registrationToken(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
