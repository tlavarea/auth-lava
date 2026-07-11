package com.lava.boot.autoconfigure.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cookie")
public record CookieProperties(String domain) {}
