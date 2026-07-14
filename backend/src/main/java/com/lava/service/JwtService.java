package com.lava.service;

import com.lava.security.AuthUserPrincipal;
import io.jsonwebtoken.Claims;
import java.security.PublicKey;
import java.time.Duration;

public interface JwtService {

    // Distinguishes a registration bridge token (see #generateRegistrationToken) from a real
    // access token at parse time - both are signed with the same key, so RegistrationServiceImpl
    // checks this claim defensively before trusting a token's subject as a verified email.
    String REGISTRATION_TOKEN_PURPOSE_CLAIM = "purpose";
    String REGISTRATION_TOKEN_PURPOSE = "registration";

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

    /**
     * Generates a short-lived, purpose-scoped bridge token used to prove that the caller of {@code /register/complete}
     * is the same session that already verified the given email during registration - no {@code user} row exists yet at
     * this point, so this carries an email subject rather than a user id and none of the role/authority claims a real
     * access token has.
     *
     * @param email - the email address that was just verified.
     * @param ttl - how long the token remains valid.
     * @return the signed JWT.
     */
    String generateRegistrationToken(String email, Duration ttl);

    Claims parseAndValidate(String token);

    /**
     * The RSA public key used to verify tokens issued by {@link #generateAccessToken} /
     * {@link #generateRegistrationToken}, exposed so it can be published via {@code JwksController} for other services
     * to verify tokens independently.
     *
     * @return the public key.
     */
    PublicKey getPublicKey();

    /**
     * The key ID (`kid`) header stamped on every token this service issues, matching the JWK published at
     * {@code /.well-known/jwks.json}.
     *
     * @return the key ID.
     */
    String getKeyId();
}
