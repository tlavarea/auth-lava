package com.lava.service;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.MfaAuthorities;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtServiceImpl(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());

        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must decode to at least 256 bits");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(AuthUserPrincipal principal, boolean mfaEnrolled, boolean totpVerified) {
        Instant now = Instant.now();

        List<String> authorities = new ArrayList<>(principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        if (mfaEnrolled) {
            authorities.add(MfaAuthorities.MFA_ENROLLED_AUTHORITY);
        }

        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(factorClaim(FactorGrantedAuthority.PASSWORD_AUTHORITY, now));

        if (totpVerified) {
            factors.add(factorClaim(MfaAuthorities.TOTP_FACTOR_AUTHORITY, now));
        }

        return Jwts.builder()
                .subject(String.valueOf(principal.getUserId()))
                .claim("email", principal.getEmail())
                .claim("emailVerified", principal.isEmailVerified())
                .claim("status", principal.getStatus())
                .claim("authorities", authorities)
                .claim("factors", factors)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Builds a single {@code factors} claim entry representing one verified authentication factor.
     *
     * @param authority - the factor's authority string, e.g. {@code FACTOR_PASSWORD}.
     * @param issuedAt - when the factor was satisfied.
     * @return the claim entry.
     */
    private static Map<String, Object> factorClaim(String authority, Instant issuedAt) {
        return Map.of("authority", authority, "issuedAt", issuedAt.toString());
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    @Override
    public String generateRegistrationToken(String email, Duration ttl) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(email)
                .claim(REGISTRATION_TOKEN_PURPOSE_CLAIM, REGISTRATION_TOKEN_PURPOSE)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
