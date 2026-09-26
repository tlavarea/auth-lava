package com.lava.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
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

        assertThat(identity).contains(new OAuthIdentity("google", "g-123", "user@example.com", true, null));
    }

    @Test
    void from_google_unverifiedEmail_producesUnverifiedIdentity() {
        OAuth2AuthenticationToken token = googleToken(Map.of(
                "sub", "g-123",
                "email", "user@example.com",
                "email_verified", false));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("google", "g-123", "user@example.com", false, null));
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

        assertThat(identity).contains(new OAuthIdentity("github", "123", "verified@example.com", true, null));
    }

    @Test
    void from_github_emailWithoutVerifiedMarker_producesUnverifiedIdentity() {
        // Simulates GithubEmailBackfillOAuth2UserService leaving the raw /user.email
        // attribute untouched because no verified primary email was found - this must
        // never be treated as verified even though "email" is non-null.
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 123, "email", "public@example.com"));

        Optional<OAuthIdentity> identity = OAuthIdentityMapper.from(token);

        assertThat(identity).contains(new OAuthIdentity("github", "123", "public@example.com", false, null));
    }

    @Test
    void from_github_noEmailAtAll_isEmpty() {
        OAuth2AuthenticationToken token = githubToken(Map.of("id", 123));

        assertThat(OAuthIdentityMapper.from(token)).isEmpty();
    }

    @Test
    @DisplayName("takes OIDC's name claim as the display name")
    void from_google_nameClaim_becomesDisplayName() {
        OAuth2AuthenticationToken token = googleToken(
                Map.of("sub", "g-123", "email", "user@example.com", "email_verified", true, "name", "Ada Lovelace"));

        assertThat(OAuthIdentityMapper.from(token).orElseThrow().displayName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("falls back to given and family name when there is no single name claim")
    void from_google_givenAndFamilyName_areJoined() {
        OAuth2AuthenticationToken token = googleToken(Map.of(
                "sub", "g-123",
                "email", "user@example.com",
                "email_verified", true,
                "given_name", "Ada",
                "family_name", "Lovelace"));

        assertThat(OAuthIdentityMapper.from(token).orElseThrow().displayName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("has no name for Apple, whose ID token carries none - the success handler supplies it")
    void from_apple_hasNoDisplayName() {
        OAuth2AuthenticationToken token =
                googleToken(Map.of("sub", "001.abc", "email", "user@privaterelay.appleid.com", "email_verified", true));

        assertThat(OAuthIdentityMapper.from(token).orElseThrow().displayName()).isNull();
    }

    @Test
    @DisplayName("prefers GitHub's name but settles for the handle, which is always set")
    void from_github_nameThenLogin() {
        OAuth2AuthenticationToken named = githubToken(Map.of(
                "id", 123, "email", "a@example.com", "emailVerified", true, "name", "Ada Lovelace", "login", "ada"));
        OAuth2AuthenticationToken unnamed =
                githubToken(Map.of("id", 123, "email", "a@example.com", "emailVerified", true, "login", "ada"));

        assertThat(OAuthIdentityMapper.from(named).orElseThrow().displayName()).isEqualTo("Ada Lovelace");
        assertThat(OAuthIdentityMapper.from(unnamed).orElseThrow().displayName())
                .isEqualTo("ada");
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
