package com.lava.repository;

import com.lava.model.database.tables.pojos.MfaBackupCode;
import java.time.LocalDateTime;
import java.util.List;

public interface MfaBackupCodeRepository {

    void deleteAllByUserId(Long userId);

    /**
     * Fetches all not-yet-used backup codes for the given user, so the caller can compare a guessed code against each
     * stored hash in constant time (see {@link com.lava.security.Hasher#matches(String, String)}) rather than filtering
     * by hash equality in SQL.
     *
     * @param userId - the user to fetch unused backup codes for.
     * @return the user's unused backup codes (at most {@code mfa.backup-code-count}, a small bounded set).
     */
    List<MfaBackupCode> findAllUnusedByUserId(Long userId);

    void insertBatch(Long userId, List<String> codeHashes);

    void markUsed(Long id, LocalDateTime usedAt);
}
