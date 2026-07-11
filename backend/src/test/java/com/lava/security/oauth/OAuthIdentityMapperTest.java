package com.lava.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class OAuthIdentityMapperTest {

    @Test
    void from_google_verifiedEmail_producesVerifiedIdentity() {
        OAuth2AuthenticationToken token = googleToken(Map.of(
                "sub", "g-123",
                "email", "user@example.com",
                "email_verified", true));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("google", "g-123", "user@example.com", true));
    }

    @Test
    void from_google_unverifiedEmail_producesUnverifiedIdentity() {
        OAuth2AuthenticationToken token = googleToken(Map.of(
                "sub", "g-123",
                "email", "user@example.com",
                "email_verified", false));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("google", "g-123", "user@example.com", false));
    }

    @Test
    void from_google_noEmailClaim_isEmpty() {
        OAuth2AuthenticationToken token = googleToken(Map.of("sub", "g-123"));

        assertThat(OAuthIdentityMapper.from(token)).isEmpty();
    }

    @Test
    void from_github_backfilledVerifiedEmail_producesVerifiedIdentity() {
        OAuth2AuthenticationToken token =
                githubToken(Map.of("id", 123, "email", "verified@example.com", "emailVerified", true));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("github", "123", "verified@example.com", true));
    }

    @Test
    void from_github_emailWithoutVerifiedMarker_producesUnverifiedIdentity() {
        // Simulates GithubEmailBackfillOAuth2UserService leaving the raw /user.email
        // attribute untouched because no verified primary email was found - this must
        // never be treated as verified even though "email" is non-null.
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 123, "email", "public@example.com"));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("github", "123", "public@example.com", false));
    }

    @Test
    void from_github_noEmailAtAll_isEmpty() {
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 123));

        assertThat(OAuthIdentityMapper.from(token)).isEmpty();
    }

    private static OAuth2AuthenticationToken googleToken(Map<String, Object> claims) {
        Map<String, Object> withSubject = new HashMap<>(claims);
        withSubject.putIfAbsent("sub", "g-fallback");

        OidcIdToken idToken =
                new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), withSubject);
        DefaultOidcUser oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        return new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "google");
    }

    private static OAuth2AuthenticationToken githubToken(Map<String, Object> attributes) {
        DefaultOAuth2User oAuth2User =
                new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "id");
        return new OAuth2AuthenticationToken(oAuth2User, oAuth2User.getAuthorities(), "github");
    }
}
