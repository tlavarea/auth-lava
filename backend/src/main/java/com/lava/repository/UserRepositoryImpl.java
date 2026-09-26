package com.lava.repository;

import static com.lava.model.database.Tables.USER;

import com.lava.model.database.AbstractSpringDAOImpl;
import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.tables.records.UserRecord;
import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record8;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UserRepositoryImpl extends AbstractSpringDAOImpl<UserRecord, User, Long> implements UserRepository {

    private final DSLContext dsl;

    public UserRepositoryImpl(DSLContext dsl) {
        super(USER, User.class);
        this.dsl = dsl;
    }

    @Override
    public boolean existsByEmail(String email) {
        return this.dsl.fetchExists(dsl.selectFrom(USER).where(USER.EMAIL.eq(normalize(email))));
    }

    @Override
    public Optional<AuthUserView> findAuthUserByEmail(String email) {
        return this.fetchAuthUserView(USER.EMAIL.eq(normalize(email)));
    }

    @Override
    public Optional<AuthUserView> findAuthUserById(Long id) {
        return this.fetchAuthUserView(USER.ID.eq(id));
    }

    @Override
    public Long getId(User object) {
        return object.id();
    }

    @Override
    @Transactional
    public Optional<User> insert(String email, String passwordHash) {
        return this.dsl
                .insertInto(USER)
                .set(USER.EMAIL, normalize(email))
                .set(USER.PASSWORD_HASH, passwordHash)
                .returning()
                .fetchOptionalInto(User.class);
    }

    @Override
    @Transactional
    public Optional<User> insertVerified(String email, String passwordHash) {
        return this.dsl
                .insertInto(USER)
                .set(USER.EMAIL, normalize(email))
                .set(USER.PASSWORD_HASH, passwordHash)
                .set(USER.EMAIL_VERIFIED, true)
                .returning()
                .fetchOptionalInto(User.class);
    }

    @Override
    @Transactional
    public Optional<User> insertVerifiedFromOAuth(String email, String displayName) {
        return this.dsl
                .insertInto(USER)
                .set(USER.EMAIL, normalize(email))
                .set(USER.EMAIL_VERIFIED, true)
                .set(USER.DISPLAY_NAME, StringUtils.trimToNull(displayName))
                .returning()
                .fetchOptionalInto(User.class);
    }

    @Override
    public void backfillDisplayName(Long userId, String displayName) {
        String name = StringUtils.trimToNull(displayName);
        if (name == null) {
            return;
        }

        this.dsl
                .update(USER)
                .set(USER.DISPLAY_NAME, name)
                .where(USER.ID.eq(userId).and(USER.DISPLAY_NAME.isNull()))
                .execute();
    }

    /**
     * Creates the {@link AuthUserView} object from the user, role, and permission tables.
     *
     * @param condition - the where condition for the query.
     * @return the {@link AuthUserView} object.
     */
    private Optional<AuthUserView> fetchAuthUserView(Condition condition) {
        List<Record8<Long, String, String, String, Boolean, String, String, String>> records = this.dsl
                .select(
                        USER.ID,
                        USER.EMAIL,
                        USER.PASSWORD_HASH,
                        USER.STATUS,
                        USER.EMAIL_VERIFIED,
                        USER.DISPLAY_NAME,
                        USER.role().NAME.as("roleName"),
                        USER.role().permission().NAME.as("permissionName"))
                .from(USER)
                .leftOuterJoin(USER.role())
                .leftOuterJoin(USER.role().permission())
                .where(condition)
                .fetch();

        if (records.isEmpty()) {
            return Optional.empty();
        }

        Set<String> roles = records.stream()
                .map(rec -> rec.get("roleName", String.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> permissions = records.stream()
                .map(rec -> rec.get("permissionName", String.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return Optional.of(AuthUserViewBuilder.builder()
                .id(records.getFirst().get(USER.ID))
                .email(records.getFirst().get(USER.EMAIL))
                .emailVerified(records.getFirst().get(USER.EMAIL_VERIFIED))
                .displayName(records.getFirst().get(USER.DISPLAY_NAME))
                .passwordHash(records.getFirst().get(USER.PASSWORD_HASH))
                .permissions(permissions)
                .roles(roles)
                .status(records.getFirst().get(USER.STATUS))
                .build());
    }

    @Override
    @Transactional
    public void recordLogin(Long userId) {
        this.dsl
                .update(USER)
                .set(USER.LAST_LOGIN_AT, LocalDateTime.now())
                .where(USER.ID.eq(userId))
                .execute();
    }

    @Override
    @Transactional
    public void updatePasswordHash(Long userId, String passwordHash) {
        this.dsl
                .update(USER)
                .set(USER.PASSWORD_HASH, passwordHash)
                .where(USER.ID.eq(userId))
                .execute();
    }

    @Override
    @Transactional
    public void updateEmail(Long userId, String newEmail) {
        this.dsl
                .update(USER)
                .set(USER.EMAIL, normalize(newEmail))
                .set(USER.EMAIL_VERIFIED, true)
                .where(USER.ID.eq(userId))
                .execute();
    }

    // Emails are stored lower-cased so the plain UNIQUE constraint on the column
    // enforces case-insensitive uniqueness without needing CITEXT or a functional index.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
