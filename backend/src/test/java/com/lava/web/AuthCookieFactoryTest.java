package com.lava.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.CookieProperties;
import com.lava.boot.autoconfigure.app.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class AuthCookieFactoryTest {

    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "private-key", "public-key", "key-id", "issuer", Duration.ofMinutes(15), Duration.ofDays(30));

    @Test
    void accessTokenCookie_hasExpectedAttributes() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties(""), JWT_PROPERTIES);

        ResponseCookie cookie = factory.accessTokenCookie("token-value");

        assertThat(cookie.getName()).isEqualTo(AuthCookieFactory.ACCESS_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void refreshTokenCookie_hasExpectedAttributes() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties(""), JWT_PROPERTIES);

        ResponseCookie cookie = factory.refreshTokenCookie("refresh-value");

        assertThat(cookie.getName()).isEqualTo(AuthCookieFactory.REFRESH_TOKEN_COOKIE);
        assertThat(cookie.getValue()).isEqualTo("refresh-value");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void clearedAccessTokenCookie_hasEmptyValueAndZeroMaxAge() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties(""), JWT_PROPERTIES);

        ResponseCookie cookie = factory.clearedAccessTokenCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void clearedRefreshTokenCookie_hasEmptyValueAndZeroMaxAge() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties(""), JWT_PROPERTIES);

        ResponseCookie cookie = factory.clearedRefreshTokenCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    void accessTokenCookie_blankDomain_doesNotSetDomain() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties(""), JWT_PROPERTIES);

        ResponseCookie cookie = factory.accessTokenCookie("token-value");

        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void accessTokenCookie_nonBlankDomain_setsDomain() {
        AuthCookieFactory factory = new AuthCookieFactory(new CookieProperties("example.com"), JWT_PROPERTIES);

        ResponseCookie cookie = factory.accessTokenCookie("token-value");

        assertThat(cookie.getDomain()).isEqualTo("example.com");
    }
}
