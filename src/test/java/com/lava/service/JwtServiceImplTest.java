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
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtServiceImplTest {

    private static final String VALID_SECRET = randomBase64Secret();

    @Test
    void constructor_validSecret_succeeds() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));

        assertThat(service).isNotNull();
    }

    @Test
    void constructor_secretDecodesToFewerThan256Bits_throwsIllegalStateException() {
        String shortSecret = Encoders.BASE64.encode(new byte[16]);

        assertThatThrownBy(() -> new JwtServiceImpl(properties(shortSecret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
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
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
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
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
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
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
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
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));

        assertThat(service.getAccessTokenTtlSeconds())
                .isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void parseAndValidate_roundTripsGeneratedToken() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
        String token = service.generateAccessToken(principal(), false, false);

        Claims claims = service.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void parseAndValidate_wrongIssuer_throwsJwtException() {
        JwtServiceImpl issuerA = new JwtServiceImpl(properties(VALID_SECRET, "issuer-a"));
        JwtServiceImpl issuerB = new JwtServiceImpl(properties(VALID_SECRET, "issuer-b"));
        String token = issuerA.generateAccessToken(principal(), false, false);

        assertThatThrownBy(() -> issuerB.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAndValidate_expiredToken_throwsExpiredJwtException() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));
        SecretKey key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(VALID_SECRET));
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String expiredToken = Jwts.builder()
                .subject("42")
                .issuer("auth-lava-test")
                .issuedAt(Date.from(past.minus(Duration.ofMinutes(15))))
                .expiration(Date.from(past))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service.parseAndValidate(expiredToken)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseAndValidate_malformedToken_throwsJwtException() {
        JwtServiceImpl service = new JwtServiceImpl(properties(VALID_SECRET));

        assertThatThrownBy(() -> service.parseAndValidate("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void parseAndValidate_signedWithDifferentKey_throwsSignatureException() {
        JwtServiceImpl serviceA = new JwtServiceImpl(properties(VALID_SECRET));
        JwtServiceImpl serviceB = new JwtServiceImpl(properties(randomBase64Secret()));
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

    private static JwtProperties properties(String secret) {
        return properties(secret, "auth-lava-test");
    }

    private static JwtProperties properties(String secret, String issuer) {
        return new JwtProperties(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(30));
    }

    private static String randomBase64Secret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
