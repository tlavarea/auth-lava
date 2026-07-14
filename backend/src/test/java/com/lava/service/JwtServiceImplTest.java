package com.lava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.security.AuthUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.SignatureException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtServiceImplTest {

    private static final KeyPair VALID_KEY_PAIR = Jwts.SIG.RS256.keyPair().build();

    @Test
    void constructor_validKeys_succeeds() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));

        assertThat(service).isNotNull();
    }

    @Test
    void constructor_keySmallerThan2048Bits_throwsIllegalStateException() {
        JwtProperties shortKeyProperties = properties(shortKeyPair());

        assertThatThrownBy(() -> new JwtServiceImpl(shortKeyProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048 bits");
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        AuthUserPrincipal principal = principal();

        String token = service.generateAccessToken(principal, false, false);
        Claims claims = service.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("emailVerified", Boolean.class)).isTrue();
        assertThat(claims.get("status", String.class)).isEqualTo("active");
        assertThat(claims.get("authorities", List.class)).containsExactlyInAnyOrder("ROLE_MEMBER");
        assertThat(claims.getIssuer()).isEqualTo("auth-lava-test");
    }

    @Test
    void generateAccessToken_mfaEnrolled_addsMfaEnrolledAuthorityButNotTotpFactor() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        AuthUserPrincipal principal = principal();

        String token = service.generateAccessToken(principal, true, false);
        Claims claims = service.parseAndValidate(token);

        assertThat(claims.get("authorities", List.class)).contains("MFA_ENROLLED");
        List<Map<String, Object>> factors = claims.get("factors", List.class);
        assertThat(factors).hasSize(1);
        assertThat(factors.getFirst().get("authority")).isEqualTo("FACTOR_PASSWORD");
    }

    @Test
    void generateAccessToken_totpVerified_addsTotpFactorClaim() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        AuthUserPrincipal principal = principal();

        String token = service.generateAccessToken(principal, true, true);
        Claims claims = service.parseAndValidate(token);

        List<Map<String, Object>> factors = claims.get("factors", List.class);
        assertThat(factors)
                .extracting(factor -> factor.get("authority"))
                .containsExactlyInAnyOrder("FACTOR_PASSWORD", "FACTOR_TOTP");
        assertThat(factors)
                .allSatisfy(
                        factor -> assertThat((String) factor.get("issuedAt")).isNotBlank());
    }

    @Test
    void generateAccessToken_totpVerifiedButNotEnrolled_factorsAndAuthoritiesAreIndependent() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        AuthUserPrincipal principal = principal();

        String token = service.generateAccessToken(principal, false, true);
        Claims claims = service.parseAndValidate(token);

        assertThat(claims.get("authorities", List.class)).doesNotContain("MFA_ENROLLED");
        List<Map<String, Object>> factors = claims.get("factors", List.class);
        assertThat(factors)
                .extracting(factor -> factor.get("authority"))
                .containsExactlyInAnyOrder("FACTOR_PASSWORD", "FACTOR_TOTP");
    }

    @Test
    void getAccessTokenTtlSeconds_matchesConfiguredTtl() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));

        assertThat(service.getAccessTokenTtlSeconds())
                .isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void parseAndValidate_roundTripsGeneratedToken() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        String token = service.generateAccessToken(principal(), false, false);

        Claims claims = service.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void parseAndValidate_wrongIssuer_throwsJwtException() {
        JwtServiceImpl issuerA = new JwtServiceImpl(properties(VALID_KEY_PAIR, "issuer-a"));
        JwtServiceImpl issuerB = new JwtServiceImpl(properties(VALID_KEY_PAIR, "issuer-b"));
        String token = issuerA.generateAccessToken(principal(), false, false);

        assertThatThrownBy(() -> issuerB.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAndValidate_expiredToken_throwsExpiredJwtException() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String expiredToken = Jwts.builder()
                .subject("42")
                .issuer("auth-lava-test")
                .issuedAt(Date.from(past.minus(Duration.ofMinutes(15))))
                .expiration(Date.from(past))
                .signWith(VALID_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> service.parseAndValidate(expiredToken)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseAndValidate_malformedToken_throwsJwtException() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_KEY_PAIR));

        assertThatThrownBy(() -> service.parseAndValidate("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAndValidate_signedWithDifferentKey_throwsSignatureException() {
        JwtServiceImpl serviceA = new JwtServiceImpl(properties(VALID_KEY_PAIR));
        JwtServiceImpl serviceB =
                new JwtServiceImpl(properties(Jwts.SIG.RS256.keyPair().build()));
        String token = serviceA.generateAccessToken(principal(), false, false);

        assertThatThrownBy(() -> serviceB.parseAndValidate(token)).isInstanceOf(SignatureException.class);
    }

    private static AuthUserPrincipal principal() {
        return AuthUserPrincipal.builder()
                .userId(42L)
                .email("user@example.com")
                .passwordHash(null)
                .status("active")
                .emailVerified(true)
                .authorities(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
                .build();
    }

    private static JwtProperties properties(KeyPair keyPair) {
        return properties(keyPair, "auth-lava-test");
    }

    private static JwtProperties properties(KeyPair keyPair, String issuer) {
        return new JwtProperties(
                Encoders.BASE64.encode(keyPair.getPrivate().getEncoded()),
                Encoders.BASE64.encode(keyPair.getPublic().getEncoded()),
                "test-key-1",
                issuer,
                Duration.ofMinutes(15),
                Duration.ofDays(30));
    }

    private static KeyPair shortKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
