package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "oauth")
@Validated
public record OAuthProperties(
        @NotBlank String successRedirectUri, @NotBlank String failureRedirectUri) {}
