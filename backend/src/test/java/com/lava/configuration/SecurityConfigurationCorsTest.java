package com.lava.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigurationCorsTest {

    private final CorsConfigurationSource source =
            new SecurityConfiguration().corsConfigurationSource(new CorsProperties(List.of("https://platetune.com")));

    @Test
    void appleCallbackPost_hasNoCorsConfiguration() {
        // No configuration means CorsFilter lets the request through untouched, rather than rejecting Apple's origin.
        assertThat(this.source.getCorsConfiguration(request("POST", "/login/oauth2/code/apple")))
                .isNull();
    }

    @Test
    void otherCallbacks_keepCorsConfiguration() {
        assertThat(this.source.getCorsConfiguration(request("GET", "/login/oauth2/code/apple")))
                .isNotNull();
        assertThat(this.source.getCorsConfiguration(request("POST", "/login/oauth2/code/google")))
                .isNotNull();
    }

    @Test
    void api_keepsItsAllowedOrigins_andAppleIsNotOneOfThem() {
        CorsConfiguration configuration = this.source.getCorsConfiguration(request("POST", "/api/auth/login"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://platetune.com")).isEqualTo("https://platetune.com");
        assertThat(configuration.checkOrigin("https://appleid.apple.com")).isNull();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Origin", "https://appleid.apple.com");
        return request;
    }
}
