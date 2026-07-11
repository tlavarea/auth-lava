package com.lava.repository;

import static com.lava.model.database.Tables.MFA_BACKUP_CODE;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.MfaBackupCode;
import com.lava.model.database.tables.records.MfaBackupCodeRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep2;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class MfaBackupCodeRepositoryImpl extends AbstractSpringDAOImpl<MfaBackupCodeRecord, MfaBackupCode, Long>
        implements MfaBackupCodeRepository {

    private final DSLContext dsl;

    public MfaBackupCodeRepositoryImpl(DSLContext dsl) {
        super(MFA_BACKUP_CODE, MfaBackupCode.class);
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void deleteAllByUserId(Long userId) {
        this.dsl
                .deleteFrom(MFA_BACKUP_CODE)
                .where(MFA_BACKUP_CODE.USER_ID.eq(userId))
                .execute();
    }

    @Override
    public List<MfaBackupCode> findAllUnusedByUserId(Long userId) {
        return this.dsl
                .selectFrom(MFA_BACKUP_CODE)
                .where(MFA_BACKUP_CODE.USER_ID.eq(userId))
                .and(MFA_BACKUP_CODE.USED_AT.isNull())
                .fetchInto(MfaBackupCode.class);
    }

    @Override
    public Long getId(MfaBackupCode object) {
        return object.id();
    }

    @Override
    @Transactional
    public void insertBatch(Long userId, List<String> codeHashes) {
        InsertValuesStep2<MfaBackupCodeRecord, Long, String> insert =
                this.dsl.insertInto(MFA_BACKUP_CODE, MFA_BACKUP_CODE.USER_ID, MFA_BACKUP_CODE.CODE_HASH);

        for (String codeHash : codeHashes) {
            insert = insert.values(userId, codeHash);
        }

        insert.execute();
    }

    @Override
    @Transactional
    public void markUsed(Long id, LocalDateTime usedAt) {
        this.dsl
                .update(MFA_BACKUP_CODE)
                .set(MFA_BACKUP_CODE.USED_AT, usedAt)
                .where(MFA_BACKUP_CODE.ID.eq(id))
                .execute();
    }
}
