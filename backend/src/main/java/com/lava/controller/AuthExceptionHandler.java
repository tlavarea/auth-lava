package com.lava.controller;

import com.lava.exception.BreachedPasswordException;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.exception.InvalidRegistrationTokenException;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.exception.InvalidVerificationCodeException;
import com.lava.exception.MfaAlreadyEnabledException;
import com.lava.exception.MfaEnrollmentNotFoundException;
import com.lava.exception.TooManyRequestsException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({
        AuthenticationException.class,
        InvalidRefreshTokenException.class,
        InvalidRegistrationTokenException.class,
        InvalidTotpCodeException.class,
        InvalidVerificationCodeException.class
    })
    public ResponseEntity<Map<String, String>> handleAuthenticationException(RuntimeException e) {
        String message = e instanceof AuthenticationException ? "Invalid email or password" : e.getMessage();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", message));
    }

    @ExceptionHandler(MfaAlreadyEnabledException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyEnrolledException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MfaEnrollmentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMfaEnrollmentNotFound(MfaEnrollmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(BreachedPasswordException.class)
    public ResponseEntity<Map<String, String>> handleBreachedPassword(BreachedPasswordException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }
}
