package com.lava.model.web.response;

import com.lava.security.AuthUserPrincipal;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

@RecordBuilder
public record UserResponse(Long id, String email, boolean emailVerified, Set<String> authorities)
        implements UserResponseBuilder.With {

    public static UserResponse from(AuthUserPrincipal principal) {
        // Excludes MFA_ENROLLED/FACTOR_* authorities - those are internal markers embedded in the
        // JWT to drive the authorization layer's factor gate, not real roles/permissions, and
        // shouldn't leak into API responses a frontend might use for role-based UI logic.
        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> !authority.startsWith("FACTOR_") && !"MFA_ENROLLED".equals(authority))
                .collect(Collectors.toSet());

        return UserResponseBuilder.builder()
                .id(principal.getUserId())
                .email(principal.getUsername())
                .emailVerified(principal.isEmailVerified())
                .authorities(authorities)
                .build();
    }
}
