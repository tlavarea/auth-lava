package com.lava.security.oauth;

/**
 * What a provider told us about whoever just signed in.
 *
 * @param provider - the registration id: "google", "github", "apple".
 * @param providerUserId - the provider's own immutable id for this account.
 * @param email - the address, normalised by the repository, not here.
 * @param emailVerified - whether the <em>provider</em> vouches for the address. Never inferred from its presence:
 *     linking an existing account on an unverified email is account takeover.
 * @param displayName - the human name, or null. Optional on purpose - GitHub users need not set one, and Apple sends it
 *     on the first authorization only (see {@code AppleUserName}). Never used to identify anyone.
 */
public record OAuthIdentity(
        String provider, String providerUserId, String email, boolean emailVerified, String displayName) {

    /** @return a copy carrying {@code displayName}, for a name that arrives from outside the principal. */
    public OAuthIdentity withDisplayName(String displayName) {
        return new OAuthIdentity(this.provider, this.providerUserId, this.email, this.emailVerified, displayName);
    }
}
