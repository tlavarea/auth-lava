package com.lava.repository;

import static com.lava.model.database.Tables.AUTH_THROTTLE;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.AuthThrottle;
import com.lava.model.database.tables.records.AuthThrottleRecord;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AuthThrottleRepositoryImpl extends AbstractSpringDAOImpl<AuthThrottleRecord, AuthThrottle, Long>
        implements AuthThrottleRepository {

    private final DSLContext dsl;

    public AuthThrottleRepositoryImpl(DSLContext dsl) {
        super(AUTH_THROTTLE, AuthThrottle.class);
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void clear(String scope, String identifier) {
        this.dsl
                .deleteFrom(AUTH_THROTTLE)
                .where(AUTH_THROTTLE.SCOPE.eq(scope))
                .and(AUTH_THROTTLE.IDENTIFIER.eq(normalize(identifier)))
                .execute();
    }

    @Override
    public Optional<AuthThrottle> find(String scope, String identifier) {
        return this.dsl
                .selectFrom(AUTH_THROTTLE)
                .where(AUTH_THROTTLE.SCOPE.eq(scope))
                .and(AUTH_THROTTLE.IDENTIFIER.eq(normalize(identifier)))
                .fetchOptionalInto(AuthThrottle.class);
    }

    @Override
    public Long getId(AuthThrottle object) {
        return object.id();
    }

    @Override
    @Transactional
    public void upsertFailure(
            String scope, String identifier, int failedCount, LocalDateTime lockedUntil, LocalDateTime updatedAt) {
        this.dsl
                .insertInto(AUTH_THROTTLE)
                .set(AUTH_THROTTLE.SCOPE, scope)
                .set(AUTH_THROTTLE.IDENTIFIER, normalize(identifier))
                .set(AUTH_THROTTLE.FAILED_COUNT, failedCount)
                .set(AUTH_THROTTLE.LOCKED_UNTIL, lockedUntil)
                .set(AUTH_THROTTLE.UPDATED_AT, updatedAt)
                .onConflict(AUTH_THROTTLE.SCOPE, AUTH_THROTTLE.IDENTIFIER)
                .doUpdate()
                .set(AUTH_THROTTLE.FAILED_COUNT, failedCount)
                .set(AUTH_THROTTLE.LOCKED_UNTIL, lockedUntil)
                .set(AUTH_THROTTLE.UPDATED_AT, updatedAt)
                .execute();
    }

    // Mirrors PendingRegistrationRepositoryImpl/UserRepositoryImpl's normalization convention -
    // identifiers are compared case-insensitively (matters for email; harmless no-op for numeric
    // user-id identifiers).
    private static String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }
}
