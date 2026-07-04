package com.lava.service;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.Issued;
import com.lava.model.auth.IssuedBuilder;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 64;

    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom;

    @Override
    public Optional<RefreshToken> findForLogout(String rawToken) {
        return this.refreshTokenRepository.findByTokenHash(hash(rawToken));
    }

    @Override
    public Issued issue(Long userId) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        this.secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenTtl());
        RefreshToken row = this.refreshTokenRepository.insert(userId, hash(rawToken), expiresAt);

        return IssuedBuilder.builder()
                .expiresAt(expiresAt)
                .id(row.id())
                .rawToken(rawToken)
                .userId(userId)
                .build();
    }

    @Override
    public void revoke(Long refreshTokenId) {
        this.refreshTokenRepository.revoke(refreshTokenId, LocalDateTime.now());
    }

    @Override
    public void revokeAllForUser(Long userId) {
        this.refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public Issued rotate(RefreshToken old) {
        Issued next = issue(old.userId());
        this.refreshTokenRepository.revokeAndReplace(old.id(), next.id(), LocalDateTime.now());
        return next;
    }

    @Override
    public RefreshToken validateForRotation(String rawToken) {
        RefreshToken row = this.refreshTokenRepository
                .findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (row.revokedAt() != null) {
            log.warn(
                    "validateForRotation::reuse of revoked token detected, revoking all sessions for user {}",
                    LogSanitizer.sanitize(row.userId()));
            this.refreshTokenRepository.revokeAllForUser(row.userId(), LocalDateTime.now());
            throw new InvalidRefreshTokenException();
        }

        if (row.expiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        return row;
    }

    /**
     * Creates a new hashed token.
     *
     * @param rawToken - the token to hash.
     * @return the token as a hashed value.
     */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            log.error("hash::error: {}", LogSanitizer.sanitize(e.getMessage()), e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
