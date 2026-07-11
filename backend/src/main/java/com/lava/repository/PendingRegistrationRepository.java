package com.lava.repository;

import com.lava.model.database.tables.pojos.PendingRegistration;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationRepository {

    void deleteByEmail(String email);

    Optional<PendingRegistration> findByEmail(String email);

    void incrementAttempt(Long id);

    void markVerified(Long id, LocalDateTime verifiedAt);

    /**
     * Creates a fresh pending registration row for the given email, or overwrites the existing one if a signup was
     * already in progress for it - resetting the attempt count and clearing any prior verification, since the old code
     * is no longer valid once a new one is issued.
     *
     * @param email - the email address being registered.
     * @param codeHash - the SHA-256 hash of the newly generated verification code.
     * @param issuedAt - when this code was generated (also used as the resend-cooldown reference point).
     * @param expiresAt - when this code stops being acceptable.
     * @return the upserted row.
     */
    PendingRegistration upsertCode(String email, String codeHash, LocalDateTime issuedAt, LocalDateTime expiresAt);
}
