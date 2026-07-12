package com.lava.repository;

import com.lava.model.database.tables.pojos.PendingEmailChange;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingEmailChangeRepository {

    void deleteByUserId(Long userId);

    Optional<PendingEmailChange> findByUserId(Long userId);

    void incrementAttempt(Long id);

    /**
     * Creates a fresh pending email change row for the given user, or overwrites the existing one if a change was
     * already in progress - resetting the attempt count, since the old code is no longer valid once a new one is
     * issued.
     *
     * @param userId - the id of the user requesting the change.
     * @param newEmail - the email address being switched to.
     * @param codeHash - the SHA-256 hash of the newly generated verification code.
     * @param issuedAt - when this code was generated (also used as the resend-cooldown reference point).
     * @param expiresAt - when this code stops being acceptable.
     * @return the upserted row.
     */
    PendingEmailChange upsertCode(
            Long userId, String newEmail, String codeHash, LocalDateTime issuedAt, LocalDateTime expiresAt);
}
