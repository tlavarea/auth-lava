package com.lava.model.auth;

import com.lava.security.AuthUserPrincipal;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds, AuthUserPrincipal principal)
        implements TokenPairBuilder.With {}
