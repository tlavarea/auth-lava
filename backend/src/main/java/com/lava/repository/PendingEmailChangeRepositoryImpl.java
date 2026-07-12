package com.lava.repository;

import static com.lava.model.database.Tables.PENDING_EMAIL_CHANGE;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.PendingEmailChange;
import com.lava.model.database.tables.records.PendingEmailChangeRecord;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PendingEmailChangeRepositoryImpl
        extends AbstractSpringDAOImpl<PendingEmailChangeRecord, PendingEmailChange, Long>
        implements PendingEmailChangeRepository {

    private final DSLContext dsl;

    public PendingEmailChangeRepositoryImpl(DSLContext dsl) {
        super(PENDING_EMAIL_CHANGE, PendingEmailChange.class);
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        this.dsl
                .deleteFrom(PENDING_EMAIL_CHANGE)
                .where(PENDING_EMAIL_CHANGE.USER_ID.eq(userId))
                .execute();
    }

    @Override
    public Optional<PendingEmailChange> findByUserId(Long userId) {
        return this.dsl
                .selectFrom(PENDING_EMAIL_CHANGE)
                .where(PENDING_EMAIL_CHANGE.USER_ID.eq(userId))
                .fetchOptionalInto(PendingEmailChange.class);
    }

    @Override
    public Long getId(PendingEmailChange object) {
        return object.id();
    }

    @Override
    @Transactional
    public void incrementAttempt(Long id) {
        this.dsl
                .update(PENDING_EMAIL_CHANGE)
                .set(PENDING_EMAIL_CHANGE.ATTEMPT_COUNT, PENDING_EMAIL_CHANGE.ATTEMPT_COUNT.plus(1))
                .where(PENDING_EMAIL_CHANGE.ID.eq(id))
                .execute();
    }

    @Override
    @Transactional
    public PendingEmailChange upsertCode(
            Long userId, String newEmail, String codeHash, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return this.dsl
                .insertInto(PENDING_EMAIL_CHANGE)
                .set(PENDING_EMAIL_CHANGE.USER_ID, userId)
                .set(PENDING_EMAIL_CHANGE.NEW_EMAIL, normalize(newEmail))
                .set(PENDING_EMAIL_CHANGE.CODE_HASH, codeHash)
                .set(PENDING_EMAIL_CHANGE.CREATED_AT, issuedAt)
                .set(PENDING_EMAIL_CHANGE.EXPIRES_AT, expiresAt)
                .onConflict(PENDING_EMAIL_CHANGE.USER_ID)
                .doUpdate()
                .set(PENDING_EMAIL_CHANGE.NEW_EMAIL, normalize(newEmail))
                .set(PENDING_EMAIL_CHANGE.CODE_HASH, codeHash)
                .set(PENDING_EMAIL_CHANGE.CREATED_AT, issuedAt)
                .set(PENDING_EMAIL_CHANGE.EXPIRES_AT, expiresAt)
                .set(PENDING_EMAIL_CHANGE.ATTEMPT_COUNT, 0)
                .returning()
                .fetchOptionalInto(PendingEmailChange.class)
                .orElseThrow(() -> new IllegalStateException("upsert into pending_email_change did not return a row"));
    }

    // Mirrors UserRepositoryImpl's normalization: emails are stored lower-cased so a plain
    // UNIQUE constraint enforces case-insensitive uniqueness.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
