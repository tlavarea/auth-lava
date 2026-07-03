package com.lava.security;

import java.util.Collection;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AuthUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final String status;
    private final boolean emailVerified;
    private final Set<GrantedAuthority> authorities;

    public AuthUserPrincipal(
            Long userId,
            String email,
            String passwordHash,
            String status,
            boolean emailVerified,
            Set<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
        this.emailVerified = emailVerified;
        this.authorities = authorities;
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
