package com.lava.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class AuthUserPrincipalTest {

    @Test
    void from_mapsRolesAndPermissionsToAuthorities() {
        AuthUserView view = AuthUserViewBuilder.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status("active")
                .emailVerified(true)
                .roles(Set.of("member"))
                .permissions(Set.of("users:read"))
                .build();

        AuthUserPrincipal principal = AuthUserPrincipal.from(view);

        assertThat(principal.getAuthorities().stream().map(GrantedAuthority::getAuthority))
                .containsExactlyInAnyOrder("ROLE_MEMBER", "users:read");
        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.getUserId()).isEqualTo(1L);
    }

    @Test
    void isAccountNonLocked_activeUser_isTrue() {
        assertThat(principal("active").isAccountNonLocked()).isTrue();
    }

    @Test
    void isAccountNonLocked_suspendedUser_isFalse() {
        assertThat(principal("suspended").isAccountNonLocked()).isFalse();
    }

    @Test
    void isEnabled_activeUser_isTrue() {
        assertThat(principal("active").isEnabled()).isTrue();
    }

    @Test
    void isEnabled_nonActiveUser_isFalse() {
        assertThat(principal("suspended").isEnabled()).isFalse();
    }

    @Test
    void accountAndCredentials_areAlwaysNonExpired() {
        AuthUserPrincipal principal = principal("active");

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }

    private static AuthUserPrincipal principal(String status) {
        return AuthUserPrincipal.builder()
                .userId(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .status(status)
                .emailVerified(true)
                .authorities(Set.of())
                .build();
    }
}
