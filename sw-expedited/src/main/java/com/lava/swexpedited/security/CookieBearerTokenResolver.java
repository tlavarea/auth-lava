package com.lava.swexpedited.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Reads the bearer token from the {@code ACCESS_TOKEN} cookie instead of the {@code Authorization} header - this whole
 * system is cookie-based (see auth-lava's {@code AuthCookieFactory}), never header-based, so the standard OAuth2
 * resource server bearer-token extraction doesn't apply here.
 *
 * <p>Registered as a {@code @Bean} inside {@code SecurityConfiguration} rather than a standalone {@code @Component} - a
 * plain {@code @Component} is invisible to {@code @WebMvcTest} slices, which only pick up web-layer stereotypes plus
 * whatever's explicitly {@code @Import}ed.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final String ACCESS_TOKEN_COOKIE = "ACCESS_TOKEN";

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
