package com.lava.controller;

import com.lava.logging.LogSanitizer;
import com.lava.model.mfa.TotpEnrollment;
import com.lava.model.web.request.EnrollTotpVerifyRequest;
import com.lava.model.web.request.MfaVerifyRequest;
import com.lava.model.web.response.BackupCodesResponse;
import com.lava.model.web.response.BackupCodesResponseBuilder;
import com.lava.model.web.response.TotpEnrollmentResponse;
import com.lava.model.web.response.UserResponse;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.AuthService;
import com.lava.service.MfaService;
import com.lava.web.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
@Slf4j
public class MfaController {

    private final AuthCookieFactory cookieFactory;
    private final AuthService authService;
    private final MfaService mfaService;

    @PostMapping("/enroll")
    public ResponseEntity<TotpEnrollmentResponse> enroll(@AuthenticationPrincipal AuthUserPrincipal principal) {
        TotpEnrollment enrollment = this.mfaService.startEnrollment(principal);
        log.info("enroll::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        return ResponseEntity.ok(TotpEnrollmentResponse.from(enrollment));
    }

    @PostMapping("/enroll/verify")
    public ResponseEntity<BackupCodesResponse> enrollVerify(
            @AuthenticationPrincipal AuthUserPrincipal principal, @Valid @RequestBody EnrollTotpVerifyRequest request) {
        List<String> backupCodes = this.mfaService.confirmEnrollment(principal, request.mfaMethodId(), request.code());
        log.info("enrollVerify::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        return ResponseEntity.ok(
                BackupCodesResponseBuilder.builder().backupCodes(backupCodes).build());
    }

    // Not gated by the MFA authorization requirement (see SecurityConfiguration) - the caller
    // only holds the password-only-factor access token issued by /login, which is exactly what
    // this endpoint exists to upgrade.
    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verify(
            @AuthenticationPrincipal AuthUserPrincipal principal,
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE) String refreshTokenCookie,
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletResponse response) {
        String accessToken = this.authService.completeMfaVerification(principal, refreshTokenCookie, request.code());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                this.cookieFactory.accessTokenCookie(accessToken).toString());
        log.info("verify::userId: {}", LogSanitizer.sanitize(principal.getUserId()));
        return ResponseEntity.ok(UserResponse.from(principal));
    }
}
