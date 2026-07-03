package com.lava.model.web.response;

import com.lava.security.AuthUserPrincipal;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

public record UserResponse(Long id, String email, boolean emailVerified, Set<String> authorities) {

    public static UserResponse from(AuthUserPrincipal principal) {
        Set<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new UserResponse(
                principal.getUserId(), principal.getUsername(), principal.isEmailVerified(), authorities);
    }
}
