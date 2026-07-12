package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "auth.throttle")
@Validated
public record AuthThrottleProperties(
        @Min(1) int loginMaxAttempts,
        @NotNull Duration loginLockoutDuration,
        @Min(1) int mfaMaxAttempts,
        @NotNull Duration mfaLockoutDuration,
        @Min(1) int passwordChangeMaxAttempts,
        @NotNull Duration passwordChangeLockoutDuration) {}
