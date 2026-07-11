package com.lava.model.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.security.AuthUserPrincipal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserResponseTest {

    @Test
    void from_excludesMfaMarkerAuthoritiesButKeepsRealOnes() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(
                        new SimpleGrantedAuthority("ROLE_MEMBER"),
                        new SimpleGrantedAuthority("users:read"),
                        new SimpleGrantedAuthority("MFA_ENROLLED"),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                        FactorGrantedAuthority.fromFactor("TOTP")))
                .build();

        UserResponse response = UserResponse.from(principal);

        assertThat(response.authorities()).containsExactlyInAnyOrder("ROLE_MEMBER", "users:read");
    }

    @Test
    void from_principalCarriesMfaEnrolledMarker_setsMfaEnabledTrue() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(new SimpleGrantedAuthority("MFA_ENROLLED")))
                .build();

        assertThat(UserResponse.from(principal).mfaEnabled()).isTrue();
    }

    @Test
    void from_principalWithoutMfaEnrolledMarker_setsMfaEnabledFalse() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of())
                .build();

        assertThat(UserResponse.from(principal).mfaEnabled()).isFalse();
    }

    @Test
    void from_withExplicitMfaEnabled_overridesPrincipalAuthorities() {
        AuthUserPrincipal principal = AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(new SimpleGrantedAuthority("MFA_ENROLLED")))
                .build();

        assertThat(UserResponse.from(principal, false).mfaEnabled()).isFalse();
    }
}
