package com.lava.model.web.response;

import com.lava.security.AuthUserPrincipal;
import com.lava.security.MfaAuthorities;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

@RecordBuilder
public record UserResponse(Long id, String email, boolean emailVerified, boolean mfaEnabled, Set<String> authorities)
        implements UserResponseBuilder.With {

    /**
     * Builds a response deriving {@code mfaEnabled} from the principal's own authorities - accurate whenever the
     * principal was reconstructed from a current JWT (e.g. {@code /me}, {@code /mfa/verify}), since the
     * {@code MFA_ENROLLED} marker is baked into the token's claims at issuance time. Callers that just changed MFA
     * enrollment state within the current request (login/refresh issuing a fresh token, or disabling MFA) know the
     * correct value directly and should use {@link #from(AuthUserPrincipal, boolean)} instead.
     *
     * @param principal - the authenticated principal.
     * @return the {@link UserResponse}.
     */
    public static UserResponse from(AuthUserPrincipal principal) {
        boolean mfaEnabled = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(MfaAuthorities.MFA_ENROLLED_AUTHORITY::equals);

        return from(principal, mfaEnabled);
    }

    public static UserResponse from(AuthUserPrincipal principal, boolean mfaEnabled) {
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
                .mfaEnabled(mfaEnabled)
                .authorities(authorities)
                .build();
    }
}
