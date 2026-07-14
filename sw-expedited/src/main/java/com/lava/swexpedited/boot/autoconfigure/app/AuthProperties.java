package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Points at auth-lava's JWKS endpoint so this service can verify JWTs it issues without sharing a signing key or
 * calling back into it per-request.
 */
@ConfigurationProperties(prefix = "auth")
@Validated
public record AuthProperties(
        @NotBlank String jwksUri, @NotBlank String issuer) {}
