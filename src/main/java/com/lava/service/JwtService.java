package com.lava.service;

import com.lava.security.AuthUserPrincipal;
import io.jsonwebtoken.Claims;

public interface JwtService {

    /**
     * Generates a signed access token for the given principal.
     *
     * @param principal - the authenticated user.
     * @param mfaEnrolled - whether the user has an enabled TOTP method, embedded as a marker authority so the
     *     authorization layer only gates access for users who actually enrolled.
     * @param totpVerified - whether the TOTP (or backup code) factor has already been satisfied for this session.
     * @return the signed JWT.
     */
    String generateAccessToken(AuthUserPrincipal principal, boolean mfaEnrolled, boolean totpVerified);

    long getAccessTokenTtlSeconds();

    Claims parseAndValidate(String token);
}
