package com.lava.model.web.response;

import com.lava.model.auth.TokenPair;
import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder
public record TokenResponse(
        String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user)
        implements TokenResponseBuilder.With {

    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresInSeconds(),
                UserResponse.from(pair.principal()));
    }
}
