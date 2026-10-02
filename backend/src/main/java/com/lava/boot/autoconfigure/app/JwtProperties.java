package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param refreshTokenReuseGrace - how long after a refresh token is rotated away that presenting it again is read as a
 *     lost response rather than theft, provided its replacement was never used: the client sent the refresh, the server
 *     rotated, and the reply carrying the new cookie never arrived - dropped signal, or a page reload mid-flight.
 *     Inside it the old token is rotated again; outside it, or once the replacement has been used, reuse still revokes
 *     every session the user has. Zero turns the grace off.
 */
@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(
        @NotBlank String privateKey,
        @NotBlank String publicKey,
        @NotBlank String keyId,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration refreshTokenReuseGrace) {}
