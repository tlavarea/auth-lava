package com.lava.repository;

import com.lava.model.database.tables.pojos.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken insert(Long userId, String tokenHash, LocalDateTime expiresAt, boolean mfaVerified);

    void markMfaVerified(Long id);

    void revoke(Long id, LocalDateTime revokedAt);

    void revokeAllForUser(Long userId, LocalDateTime revokedAt);

    void revokeAndReplace(Long oldId, Long newId, LocalDateTime revokedAt);
}
