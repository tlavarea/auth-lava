package com.lava.web.filter;

import com.lava.logging.LogSanitizer;
import com.lava.security.AuthUserPrincipal;
import com.lava.service.JwtService;
import com.lava.web.AuthCookieFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        this.extractAccessToken(request).ifPresent(token -> {
            try {
                Claims claims = this.jwtService.parseAndValidate(token);
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
        });

        chain.doFilter(request, response);
    }

    /**
     * Builds the {@link AuthUserPrincipal} object from the JWT claims.
     *
     * @param claims - the JWT claims.
     * @return - the {@link AuthUserPrincipal} object with data from the JWT claims.
     */
    @SuppressWarnings("unchecked")
    private AuthUserPrincipal buildPrincipal(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get("email", String.class);
        boolean emailVerified = Boolean.TRUE.equals(claims.get("emailVerified", Boolean.class));
        String status = claims.get("status", String.class);
        List<String> authorityNames = claims.get("authorities", List.class);
        Set<GrantedAuthority> authorities =
                authorityNames.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toCollection(HashSet::new));
        List<Map<String, Object>> factors = claims.get("factors", List.class);

        if (factors != null) {
            for (Map<String, Object> factor : factors) {
                authorities.add(FactorGrantedAuthority.withAuthority((String) factor.get("authority"))
                        .issuedAt(Instant.parse((String) factor.get("issuedAt")))
                        .build());
            }
        }

        return AuthUserPrincipal.builder()
                .authorities(authorities)
                .email(email)
                .emailVerified(emailVerified)
                .passwordHash(null)
                .status(status)
                .userId(userId)
                .build();
    }

    /**
     * Grabs the access token from the request cookies.
     *
     * @param request - the {@link HttpServletRequest} object.
     * @return - the value of the access token cookie.
     */
    private Optional<String> extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }

        return Optional.empty();
    }
}
