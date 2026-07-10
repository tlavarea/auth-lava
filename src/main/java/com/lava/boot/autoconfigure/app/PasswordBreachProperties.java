package com.lava.boot.autoconfigure.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "password-breach-check")
@Validated
public record PasswordBreachProperties(boolean enabled) {}
