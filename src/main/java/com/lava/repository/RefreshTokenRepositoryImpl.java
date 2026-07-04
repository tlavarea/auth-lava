package com.lava.repository;

import static com.lava.model.database.Tables.REFRESH_TOKEN;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.RefreshToken;
import com.lava.model.database.tables.records.RefreshTokenRecord;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RefreshTokenRepositoryImpl extends AbstractSpringDAOImpl<RefreshTokenRecord, RefreshToken, Long>
        implements RefreshTokenRepository {

    private final DSLContext dsl;

    public RefreshTokenRepositoryImpl(DSLContext dsl) {
        super(REFRESH_TOKEN, RefreshToken.class);
        this.dsl = dsl;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return this.dsl
                .selectFrom(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .fetchOptionalInto(RefreshToken.class);
    }

    @Override
    public Long getId(RefreshToken object) {
        return object.id();
    }

    @Override
    @Transactional
    public RefreshToken insert(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return this.dsl
                .insertInto(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.USER_ID, userId)
                .set(REFRESH_TOKEN.TOKEN_HASH, tokenHash)
                .set(REFRESH_TOKEN.EXPIRES_AT, expiresAt)
                .returning()
                .fetchOptionalInto(RefreshToken.class)
                .orElseThrow(() -> new IllegalStateException("insert into refresh_token did not return a row"));
    }

    @Override
    @Transactional
    public void revoke(Long id, LocalDateTime revokedAt) {
        this.dsl
                .update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, revokedAt)
                .where(REFRESH_TOKEN.ID.eq(id))
                .execute();
    }

    // REQUIRES_NEW: called mid-way through RefreshTokenService.validateForRotation() when token
    // reuse is detected, right before that method throws InvalidRefreshTokenException. Since the
    // caller (AuthServiceImpl.refresh()) is @Transactional, an uncaught RuntimeException marks
    // the whole transaction rollback-only by default - which would silently undo this
    // breach-containment revocation. Running it in its own transaction means it commits
    // immediately and survives regardless of what the outer transaction does afterward.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId, LocalDateTime revokedAt) {
        this.dsl
                .update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, revokedAt)
                .where(REFRESH_TOKEN.USER_ID.eq(userId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    @Override
    @Transactional
    public void revokeAndReplace(Long oldId, Long newId, LocalDateTime revokedAt) {
        this.dsl
                .update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, revokedAt)
                .set(REFRESH_TOKEN.REPLACED_BY_ID, newId)
                .where(REFRESH_TOKEN.ID.eq(oldId))
                .execute();
    }
}
