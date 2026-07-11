package com.lava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.exception.InvalidRefreshTokenException;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.exception.MfaAlreadyEnabledException;
import com.lava.exception.MfaEnrollmentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void handleAuthenticationException_returns401WithGenericMessage() {
        ResponseEntity<?> response = this.handler.handleAuthenticationException(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "Invalid email or password"));
    }

    @Test
    void handleInvalidRefreshToken_returns401WithExceptionMessage() {
        ResponseEntity<?> response = this.handler.handleAuthenticationException(new InvalidRefreshTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "Invalid or expired refresh token"));
    }

    @Test
    void handleInvalidTotpCode_returns401WithExceptionMessage() {
        ResponseEntity<?> response = this.handler.handleAuthenticationException(new InvalidTotpCodeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "Invalid verification code"));
    }

    @Test
    void handleMfaAlreadyEnabled_returns409WithExceptionMessage() {
        ResponseEntity<?> response = this.handler.handleAlreadyEnrolledException(new MfaAlreadyEnabledException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "MFA is already enabled for this account"));
    }

    @Test
    void handleMfaEnrollmentNotFound_returns404WithExceptionMessage() {
        ResponseEntity<?> response = this.handler.handleMfaEnrollmentNotFound(new MfaEnrollmentNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "No pending MFA enrollment found"));
    }

    @Test
    void handleUnexpectedException_returns500WithGenericMessage() {
        ResponseEntity<?> response = this.handler.handleUnexpectedException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "An unexpected error occurred"));
    }
}
