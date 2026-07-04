package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "cors")
@Validated
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {}
