package com.lava.swexpedited.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @Test
    void resolve_noCookies_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(this.resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_noMatchingCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER_COOKIE", "value"));

        assertThat(this.resolver.resolve(request)).isNull();
    }

    @Test
    void resolve_accessTokenCookiePresent_returnsItsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER_COOKIE", "other-value"), new Cookie("ACCESS_TOKEN", "token-value"));

        assertThat(this.resolver.resolve(request)).isEqualTo("token-value");
    }
}
