package com.lava.model.web.response;

import com.lava.security.AuthUserPrincipal;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

@RecordBuilder
public record UserResponse(Long id, String email, boolean emailVerified, Set<String> authorities)
        implements UserResponseBuilder.With {

    public static UserResponse from(AuthUserPrincipal principal) {
        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return UserResponseBuilder.builder()
                .id(principal.getUserId())
                .email(principal.getUsername())
                .emailVerified(principal.isEmailVerified())
                .authorities(authorities)
                .build();
    }
}
