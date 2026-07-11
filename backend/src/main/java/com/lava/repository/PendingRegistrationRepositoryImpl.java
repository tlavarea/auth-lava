package com.lava.repository;

import static com.lava.model.database.Tables.PENDING_REGISTRATION;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.PendingRegistration;
import com.lava.model.database.tables.records.PendingRegistrationRecord;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PendingRegistrationRepositoryImpl
        extends AbstractSpringDAOImpl<PendingRegistrationRecord, PendingRegistration, Long>
        implements PendingRegistrationRepository {

    private final DSLContext dsl;

    public PendingRegistrationRepositoryImpl(DSLContext dsl) {
        super(PENDING_REGISTRATION, PendingRegistration.class);
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void deleteByEmail(String email) {
        this.dsl
                .deleteFrom(PENDING_REGISTRATION)
                .where(PENDING_REGISTRATION.EMAIL.eq(normalize(email)))
                .execute();
    }

    @Override
    public Optional<PendingRegistration> findByEmail(String email) {
        return this.dsl
                .selectFrom(PENDING_REGISTRATION)
                .where(PENDING_REGISTRATION.EMAIL.eq(normalize(email)))
                .fetchOptionalInto(PendingRegistration.class);
    }

    @Override
    public Long getId(PendingRegistration object) {
        return object.id();
    }

    @Override
    @Transactional
    public void incrementAttempt(Long id) {
        this.dsl
                .update(PENDING_REGISTRATION)
                .set(PENDING_REGISTRATION.ATTEMPT_COUNT, PENDING_REGISTRATION.ATTEMPT_COUNT.plus(1))
                .where(PENDING_REGISTRATION.ID.eq(id))
                .execute();
    }

    @Override
    @Transactional
    public void markVerified(Long id, LocalDateTime verifiedAt) {
        this.dsl
                .update(PENDING_REGISTRATION)
                .set(PENDING_REGISTRATION.VERIFIED_AT, verifiedAt)
                .where(PENDING_REGISTRATION.ID.eq(id))
                .execute();
    }

    @Override
    @Transactional
    public PendingRegistration upsertCode(
            String email, String codeHash, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return this.dsl
                .insertInto(PENDING_REGISTRATION)
                .set(PENDING_REGISTRATION.EMAIL, normalize(email))
                .set(PENDING_REGISTRATION.CODE_HASH, codeHash)
                .set(PENDING_REGISTRATION.CREATED_AT, issuedAt)
                .set(PENDING_REGISTRATION.EXPIRES_AT, expiresAt)
                .onConflict(PENDING_REGISTRATION.EMAIL)
                .doUpdate()
                .set(PENDING_REGISTRATION.CODE_HASH, codeHash)
                .set(PENDING_REGISTRATION.CREATED_AT, issuedAt)
                .set(PENDING_REGISTRATION.EXPIRES_AT, expiresAt)
                .set(PENDING_REGISTRATION.ATTEMPT_COUNT, 0)
                .setNull(PENDING_REGISTRATION.VERIFIED_AT)
                .returning()
                .fetchOptionalInto(PendingRegistration.class)
                .orElseThrow(() -> new IllegalStateException("upsert into pending_registration did not return a row"));
    }

    // Mirrors UserRepositoryImpl's normalization: emails are stored lower-cased so a plain
    // UNIQUE constraint enforces case-insensitive uniqueness.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
