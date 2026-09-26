package com.lava.security.oauth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lava.boot.autoconfigure.app.AppleProperties;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppleClientSecretFactoryTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final String TEAM_ID = "A1B2C3D4E5";
    private static final String KEY_ID = "K9Y1D2E3F4";
    private static final String SERVICES_ID = "com.platetune.web";

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKey() throws Exception {
        // P-256, which is what ES256 requires and what Apple issues.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("signs an ES256 JWT carrying the claims Apple requires")
    void mintsAppleClientSecret() {
        String secret = factory(Duration.ofMinutes(5)).createClientSecret(SERVICES_ID);

        var jwt = parser().parseSignedClaims(secret);

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
        // The Key ID travels in the header, not the claims - Apple reads it to pick a key.
        assertThat(jwt.getHeader().get("kid")).isEqualTo(KEY_ID);
        assertThat(jwt.getPayload().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getPayload().getSubject()).isEqualTo(SERVICES_ID);
        assertThat(jwt.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getPayload().getIssuedAt()).isEqualTo(Date.from(NOW));
        assertThat(jwt.getPayload().getExpiration()).isEqualTo(Date.from(NOW.plusSeconds(300)));
    }

    @Test
    @DisplayName("takes the Services ID from the caller, so it is not configured twice")
    void subjectComesFromTheRegistration() {
        String secret = factory(Duration.ofMinutes(5)).createClientSecret("com.other.web");

        assertThat(parser().parseSignedClaims(secret).getPayload().getSubject()).isEqualTo("com.other.web");
    }

    @Test
    @DisplayName("rejects a PEM .p8 with a message saying how to convert it")
    void rejectsUnconvertedPemKey() {
        String pem = Base64.getEncoder()
                .encodeToString("-----BEGIN PRIVATE KEY-----\nnot-der\n-----END PRIVATE KEY-----".getBytes());

        assertThatThrownBy(() ->
                        new AppleClientSecretFactory(new AppleProperties(TEAM_ID, KEY_ID, pem, Duration.ofMinutes(5))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl pkcs8 -topk8");
    }

    @Test
    @DisplayName("defaults the secret lifetime rather than minting a six-month credential")
    void defaultsClientSecretTtl() {
        assertThat(new AppleProperties(TEAM_ID, KEY_ID, "x", null).clientSecretTtl())
                .isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * The same fixed instant the factory signs at. Without it the parser uses the wall clock and every minted secret is
     * already expired by definition, since the factory is deliberately short-lived.
     */
    private io.jsonwebtoken.JwtParser parser() {
        return Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .clock(() -> Date.from(NOW))
                .build();
    }

    private AppleClientSecretFactory factory(Duration ttl) {
        String base64Der =
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return new AppleClientSecretFactory(
                new AppleProperties(TEAM_ID, KEY_ID, base64Der, ttl), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
