package com.lava.web.filter;

import com.lava.logging.LogSanitizer;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = this.jwtService.parseAndValidate(header.substring(BEARER_PREFIX.length()));
                AuthUserPrincipal principal = this.buildPrincipal(claims);
                Authentication authentication =
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException e) {
                log.error("jwt::rejected: {}", LogSanitizer.sanitize(e.getMessage()), e);
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private AuthUserPrincipal buildPrincipal(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        boolean emailVerified = Boolean.TRUE.equals(claims.get("emailVerified", Boolean.class));
        String status = claims.get("status", String.class);
        List<String> authorityNames = claims.get("authorities", List.class);
        Set<GrantedAuthority> authorities =
                authorityNames.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());

        return AuthUserPrincipal.builder()
                .authorities(authorities)
                .email(email)
                .emailVerified(emailVerified)
                .passwordHash(null)
                .status(status)
                .userId(userId)
                .build();
    }
}
