package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mfa")
@Validated
public record MfaProperties(
        @NotBlank String encryptionKey,
        @NotBlank String encryptionSalt,
        @Min(1) int backupCodeCount,
        @Min(6) int backupCodeLength,
        @NotBlank String totpIssuer,
        @Min(0) int totpAllowedDiscrepancy) {}
