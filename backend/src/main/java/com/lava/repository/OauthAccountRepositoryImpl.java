package com.lava.repository;

import static com.lava.model.database.Tables.OAUTH_ACCOUNT;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.OauthAccount;
import com.lava.model.database.tables.records.OauthAccountRecord;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class OauthAccountRepositoryImpl extends AbstractSpringDAOImpl<OauthAccountRecord, OauthAccount, Long>
        implements OauthAccountRepository {

    private final DSLContext dsl;

    public OauthAccountRepositoryImpl(DSLContext dsl) {
        super(OAUTH_ACCOUNT, OauthAccount.class);
        this.dsl = dsl;
    }

    @Override
    public Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId) {
        return this.dsl
                .selectFrom(OAUTH_ACCOUNT)
                .where(OAUTH_ACCOUNT.PROVIDER.eq(provider))
                .and(OAUTH_ACCOUNT.PROVIDER_USER_ID.eq(providerUserId))
                .fetchOptionalInto(OauthAccount.class);
    }

    @Override
    public Long getId(OauthAccount object) {
        return object.id();
    }

    @Override
    @Transactional
    public OauthAccount insert(Long userId, String provider, String providerUserId) {
        return this.dsl
                .insertInto(OAUTH_ACCOUNT)
                .set(OAUTH_ACCOUNT.USER_ID, userId)
                .set(OAUTH_ACCOUNT.PROVIDER, provider)
                .set(OAUTH_ACCOUNT.PROVIDER_USER_ID, providerUserId)
                .returning()
                .fetchOptionalInto(OauthAccount.class)
                .orElseThrow(() -> new IllegalStateException("insert into oauth_account did not return a row"));
    }
}
