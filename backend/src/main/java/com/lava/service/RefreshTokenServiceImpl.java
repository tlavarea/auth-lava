package com.lava.service;

import com.lava.boot.autoconfigure.app.JwtProperties;
import com.lava.exception.InvalidRefreshTokenException;
import com.lava.logging.LogSanitizer;
import com.lava.model.auth.Issued;
import com.lava.model.auth.IssuedBuilder;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.repository.RefreshTokenRepository;
import com.lava.security.Hasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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
        return this.refreshTokenRepository.findByTokenHash(Hasher.hash(rawToken));
    }

    @Override
    public Issued issue(Long userId) {
        return this.issue(userId, false);
    }

    @Override
    @Transactional
    public void markMfaVerified(String rawToken) {
        RefreshToken row = this.refreshTokenRepository
                .findByTokenHash(Hasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        this.refreshTokenRepository.markMfaVerified(row.id());
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
        Issued next = this.issue(old.userId(), Boolean.TRUE.equals(old.mfaVerified()));
        this.refreshTokenRepository.revokeAndReplace(old.id(), next.id(), LocalDateTime.now());
        return next;
    }

    @Override
    public RefreshToken validateForRotation(String rawToken) {
        RefreshToken row = this.refreshTokenRepository
                .findByTokenHash(Hasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (row.revokedAt() != null && isLostResponse(row)) {
            if (row.expiresAt().isBefore(LocalDateTime.now())) {
                throw new InvalidRefreshTokenException();
            }
            // The client never received the replacement; retire it, and let the old token be rotated again.
            log.info(
                    "validateForRotation::rotated-away token presented within the reuse grace, replacement unused - "
                            + "rotating again for user {}",
                    LogSanitizer.sanitize(row.userId()));
            this.refreshTokenRepository.revoke(row.replacedById(), LocalDateTime.now());
            return row;
        }

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
     * Whether a revoked token being presented again is a lost response rather than reuse: it was rotated away (not
     * logged out or revoked wholesale), within {@code jwt.refresh-token-reuse-grace}, and its replacement has never
     * been used - so the client cannot have received it. Anything else is treated as theft, as before.
     */
    private boolean isLostResponse(RefreshToken row) {
        if (row.replacedById() == null) {
            return false;
        }
        LocalDateTime graceEnds = row.revokedAt().plus(this.jwtProperties.refreshTokenReuseGrace());
        if (!LocalDateTime.now().isBefore(graceEnds)) {
            return false;
        }
        return this.refreshTokenRepository
                .findOptionalById(row.replacedById())
                .map(replacement -> replacement.revokedAt() == null)
                .orElse(false);
    }

    /**
     * Issues a new refresh token, carrying forward whether the MFA factor has already been satisfied for this session
     * so rotation doesn't force re-verification.
     *
     * @param userId - the user to issue the token for.
     * @param mfaVerified - whether the second MFA factor has already been satisfied for this session.
     * @return the {@link Issued} refresh token.
     */
    private Issued issue(Long userId, boolean mfaVerified) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        this.secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenTtl());
        RefreshToken row = this.refreshTokenRepository.insert(userId, Hasher.hash(rawToken), expiresAt, mfaVerified);

        return IssuedBuilder.builder()
                .expiresAt(expiresAt)
                .id(row.id())
                .rawToken(rawToken)
                .userId(userId)
                .build();
    }
}
