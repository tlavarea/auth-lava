package com.lava.web;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    public static final String ACCESS_TOKEN_COOKIE = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";

    private static final String REFRESH_TOKEN_PATH = "/api/auth";

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public ResponseCookie accessTokenCookie(String token) {
        return build(ACCESS_TOKEN_COOKIE, token, "/", jwtProperties.accessTokenTtl());
    }

    public ResponseCookie clearedAccessTokenCookie() {
        return build(ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO);
    }

    public ResponseCookie clearedRefreshTokenCookie() {
        return build(REFRESH_TOKEN_COOKIE, "", REFRESH_TOKEN_PATH, Duration.ZERO);
    }

    public ResponseCookie refreshTokenCookie(String token) {
        return build(REFRESH_TOKEN_COOKIE, token, REFRESH_TOKEN_PATH, jwtProperties.refreshTokenTtl());
    }

    /**
     * Builds the cookie based on the supplied parameters.
     *
     * @param name - the name of the cookie.
     * @param value - the value of the cookie.
     * @param path - the URL path for the cookie.
     * @param maxAge - the maximum age of the cookie.
     * @return - the {@link ResponseCookie} object.
     */
    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge);

        if (StringUtils.isNotBlank(cookieProperties.domain())) {
            builder.domain(cookieProperties.domain());
        }

        return builder.build();
    }
}
