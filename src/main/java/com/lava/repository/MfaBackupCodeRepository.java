package com.lava.repository;

import com.lava.model.database.tables.pojos.MfaBackupCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MfaBackupCodeRepository {

    void deleteAllByUserId(Long userId);

    Optional<MfaBackupCode> findUnusedByUserIdAndCodeHash(Long userId, String codeHash);

    void insertBatch(Long userId, List<String> codeHashes);

    void markUsed(Long id, LocalDateTime usedAt);
}
