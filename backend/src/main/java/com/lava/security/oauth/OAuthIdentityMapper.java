package com.lava.security.oauth;

import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OAuthIdentityMapper {

    public static Optional<OAuthIdentity> from(OAuth2AuthenticationToken authentication) {
        String provider = authentication.getAuthorizedClientRegistrationId();

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromOidcUser(provider, oidcUser);
        }

        return fromOAuth2User(provider, authentication.getPrincipal());
    }

    /**
     * GitHub's plain /user.email attribute is never trusted as verified on its own -
     * {@link GithubEmailBackfillOAuth2UserService} is the only thing that sets the "emailVerified" attribute, and only
     * after confirming a verified primary email via GitHub's /user/emails API.
     *
     * @param provider - the registration id (e.g. "github").
     * @param oAuth2User - the OAuth2 principal.
     * @return the identity, or empty if the provider gave us no email to key off of at all.
     */
    private static Optional<OAuthIdentity> fromOAuth2User(String provider, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        Object idAttribute = attributes.get("id");
        Object emailAttribute = attributes.get("email");

        if (idAttribute == null || emailAttribute == null) {
            return Optional.empty();
        }

        boolean emailVerified = Boolean.TRUE.equals(attributes.get("emailVerified"));
        // GitHub's "name" is optional and often unset; "login" is the handle and always there. Either beats nothing
        // for a greeting, and neither is ever used to identify anyone.
        String displayName = firstNonBlank(attributes.get("name"), attributes.get("login"));
        return Optional.of(new OAuthIdentity(
                provider, String.valueOf(idAttribute), (String) emailAttribute, emailVerified, displayName));
    }

    /**
     * Google's ID token already carries a verified-email claim, so no additional lookups are required here.
     *
     * @param provider - the registration id (e.g. "google").
     * @param oidcUser - the OIDC principal.
     * @return the identity, or empty if the provider gave us no email to key off of at all.
     */
    private static Optional<OAuthIdentity> fromOidcUser(String provider, OidcUser oidcUser) {
        String email = oidcUser.getEmail();

        if (email == null) {
            return Optional.empty();
        }

        boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());
        // Apple issues an ID token with no name claim at all, so this is null for Apple and the success handler
        // supplies it from the callback's form body instead.
        String displayName =
                firstNonBlank(oidcUser.getFullName(), joined(oidcUser.getGivenName(), oidcUser.getFamilyName()));
        return Optional.of(new OAuthIdentity(provider, oidcUser.getSubject(), email, emailVerified, displayName));
    }

    private static String firstNonBlank(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate instanceof String value && StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String joined(String first, String last) {
        return StringUtils.trimToNull(
                StringUtils.normalizeSpace(StringUtils.defaultString(first) + " " + StringUtils.defaultString(last)));
    }
}
