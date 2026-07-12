package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "email-change")
@Validated
public record EmailChangeProperties(
        @Min(4) int codeLength,
        @NotNull Duration codeTtl,
        @Min(1) int maxAttempts,
        @NotNull Duration resendCooldown) {}
