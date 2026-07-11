package com.lava.security;

import com.lava.model.database.view.AuthUserView;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@AllArgsConstructor
@Builder
@Getter
public class AuthUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final String status;
    private final boolean emailVerified;
    private final Set<GrantedAuthority> authorities;

    public static AuthUserPrincipal from(AuthUserView view) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        for (String role : view.roles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
        }

        for (String permission : view.permissions()) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }

        return AuthUserPrincipal.builder()
                .authorities(authorities)
                .email(view.email())
                .emailVerified(view.emailVerified())
                .passwordHash(view.passwordHash())
                .status(view.status())
                .userId(view.id())
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"suspended".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "active".equals(status);
    }
}
