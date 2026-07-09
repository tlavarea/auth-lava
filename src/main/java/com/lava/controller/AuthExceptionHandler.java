package com.lava.controller;

import com.lava.exception.EmailAlreadyRegisteredException;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.exception.InvalidTotpCodeException;
import com.lava.exception.MfaAlreadyEnabledException;
import com.lava.exception.MfaEnrollmentNotFoundException;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler({AuthenticationException.class, InvalidRefreshTokenException.class, InvalidTotpCodeException.class
    })
    public ResponseEntity<Map<String, String>> handleAuthenticationException(RuntimeException e) {
        String message =
                e instanceof AuthenticationException ? "Invalid email or password" : ExceptionUtils.getMessage(e);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", message));
    }

    @ExceptionHandler({EmailAlreadyRegisteredException.class, MfaAlreadyEnabledException.class})
    public ResponseEntity<Map<String, String>> handleAlreadyEnrolledException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ExceptionUtils.getMessage(e)));
    }

    @ExceptionHandler(MfaEnrollmentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMfaEnrollmentNotFound(MfaEnrollmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ExceptionUtils.getMessage(e)));
    }
}
