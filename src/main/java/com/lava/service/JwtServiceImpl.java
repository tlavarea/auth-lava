package com.lava.service;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.security.AuthUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
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
    public String generateAccessToken(AuthUserPrincipal principal) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(principal.getUserId()))
                .claim("email", principal.getEmail())
                .claim("emailVerified", principal.isEmailVerified())
                .claim("status", principal.getStatus())
                .claim(
                        "authorities",
                        principal.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
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
