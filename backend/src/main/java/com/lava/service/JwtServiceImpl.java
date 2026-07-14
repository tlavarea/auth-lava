package com.lava.service;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.security.AuthUserPrincipal;
import com.lava.security.MfaAuthorities;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    private static final int MIN_RSA_KEY_BITS = 2048;
    private static final String RSA_ALGORITHM = "RSA";

    private final JwtProperties properties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtServiceImpl(JwtProperties properties) {
        this.properties = properties;
        this.privateKey = decodePrivateKey(properties.privateKey());
        this.publicKey = decodePublicKey(properties.publicKey());
    }

    private static PrivateKey decodePrivateKey(String base64) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PrivateKey key = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Decoders.BASE64.decode(base64)));
            validateKeySize(key, "jwt.private-key");
            return key;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("jwt.private-key is not a valid RSA PKCS8 key", e);
        }
    }

    private static PublicKey decodePublicKey(String base64) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey key = keyFactory.generatePublic(new X509EncodedKeySpec(Decoders.BASE64.decode(base64)));
            validateKeySize(key, "jwt.public-key");
            return key;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("jwt.public-key is not a valid RSA X509 key", e);
        }
    }

    private static void validateKeySize(Key key, String propertyName) {
        if (key instanceof RSAKey rsaKey && rsaKey.getModulus().bitLength() < MIN_RSA_KEY_BITS) {
            throw new IllegalStateException(propertyName + " must be at least " + MIN_RSA_KEY_BITS + " bits");
        }
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
                .header()
                .keyId(properties.keyId())
                .and()
                .subject(String.valueOf(principal.getUserId()))
                .claim("email", principal.getEmail())
                .claim("emailVerified", principal.isEmailVerified())
                .claim("status", principal.getStatus())
                .claim("authorities", authorities)
                .claim("factors", factors)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(privateKey, Jwts.SIG.RS256)
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
                .header()
                .keyId(properties.keyId())
                .and()
                .subject(email)
                .claim(REGISTRATION_TOKEN_PURPOSE_CLAIM, REGISTRATION_TOKEN_PURPOSE)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public PublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public String getKeyId() {
        return properties.keyId();
    }
}
