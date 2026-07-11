package com.lava.repository;

import static com.lava.model.database.Tables.MFA_METHOD;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.MfaMethod;
import com.lava.model.database.tables.records.MfaMethodRecord;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MfaMethodRepositoryImpl extends AbstractSpringDAOImpl<MfaMethodRecord, MfaMethod, Long>
        implements MfaMethodRepository {

    private final DSLContext dsl;

    public MfaMethodRepositoryImpl(DSLContext dsl) {
        super(MFA_METHOD, MfaMethod.class);
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void deleteEnabledByUserIdAndType(Long userId, String type) {
        this.dsl
                .deleteFrom(MFA_METHOD)
                .where(MFA_METHOD.USER_ID.eq(userId))
                .and(MFA_METHOD.TYPE.eq(type))
                .and(MFA_METHOD.IS_ENABLED.eq(true))
                .execute();
    }

    @Override
    @Transactional
    public void deleteUnconfirmedByUserIdAndType(Long userId, String type) {
        this.dsl
                .deleteFrom(MFA_METHOD)
                .where(MFA_METHOD.USER_ID.eq(userId))
                .and(MFA_METHOD.TYPE.eq(type))
                .and(MFA_METHOD.IS_ENABLED.eq(false))
                .execute();
    }

    @Override
    public Optional<MfaMethod> findOptionalById(Long id) {
        return this.dsl.selectFrom(MFA_METHOD).where(MFA_METHOD.ID.eq(id)).fetchOptionalInto(MfaMethod.class);
    }

    @Override
    public Optional<MfaMethod> findEnabledByUserIdAndType(Long userId, String type) {
        return this.dsl
                .selectFrom(MFA_METHOD)
                .where(MFA_METHOD.USER_ID.eq(userId))
                .and(MFA_METHOD.TYPE.eq(type))
                .and(MFA_METHOD.IS_ENABLED.eq(true))
                .fetchOptionalInto(MfaMethod.class);
    }

    @Override
    public Long getId(MfaMethod object) {
        return object.id();
    }

    @Override
    @Transactional
    public MfaMethod insertPending(Long userId, String type, String secretEncrypted) {
        return this.dsl
                .insertInto(MFA_METHOD)
                .set(MFA_METHOD.USER_ID, userId)
                .set(MFA_METHOD.TYPE, type)
                .set(MFA_METHOD.SECRET_ENCRYPTED, secretEncrypted)
                .returning()
                .fetchOptionalInto(MfaMethod.class)
                .orElseThrow(() -> new IllegalStateException("insert into mfa_method did not return a row"));
    }

    @Override
    @Transactional
    public void markVerifiedAndEnabled(Long id, LocalDateTime verifiedAt) {
        this.dsl
                .update(MFA_METHOD)
                .set(MFA_METHOD.IS_ENABLED, true)
                .set(MFA_METHOD.VERIFIED_AT, verifiedAt)
                .where(MFA_METHOD.ID.eq(id))
                .execute();
    }
}
