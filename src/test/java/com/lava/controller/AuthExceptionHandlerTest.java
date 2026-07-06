package com.lava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRefreshTokenException;
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
    void handleEmailAlreadyRegistered_returns409WithExceptionMessage() {
        ResponseEntity<?> response =
                this.handler.handleEmailAlreadyRegistered(new EmailAlreadyRegisteredException("dup@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .isEqualTo(java.util.Map.of("error", "Email already registered: dup@example.com"));
    }

    @Test
    void handleInvalidRefreshToken_returns401WithExceptionMessage() {
        ResponseEntity<?> response = this.handler.handleInvalidRefreshToken(new InvalidRefreshTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("error", "Invalid or expired refresh token"));
    }
}
