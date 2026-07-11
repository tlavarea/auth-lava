package com.lava.security.oauth;

public record OAuthIdentity(String provider, String providerUserId, String email, boolean emailVerified) {}
