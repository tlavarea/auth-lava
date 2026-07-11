package com.lava.security;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Authority string constants used to represent MFA state inside JWT claims and to drive the conditional multi-factor
 * {@code AuthorizationManager} in {@code SecurityConfiguration}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MfaAuthorities {

    /** Granted once a user has successfully authenticated with a TOTP code or backup code. */
    public static final String TOTP_FACTOR_AUTHORITY = "FACTOR_TOTP";

    /**
     * Marker authority embedded in a token's claims when the user has TOTP MFA enabled, driving the conditional factor
     * requirement so non-enrolled users are never gated.
     */
    public static final String MFA_ENROLLED_AUTHORITY = "MFA_ENROLLED";
}
