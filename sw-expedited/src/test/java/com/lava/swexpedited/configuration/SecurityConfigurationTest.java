package com.lava.swexpedited.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.swexpedited.boot.autoconfigure.app.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    void jwtDecoder_buildsDecoderFromJwksUri() {
        JwtDecoder decoder =
                this.configuration.jwtDecoder(new AuthProperties("http://localhost:9999/jwks.json", "test-issuer"));

        assertThat(decoder).isNotNull();
    }

    @Test
    void jwtAuthenticationConverter_isConfigured() {
        JwtAuthenticationConverter converter = this.configuration.jwtAuthenticationConverter();

        assertThat(converter).isNotNull();
    }
}
