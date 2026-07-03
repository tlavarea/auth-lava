package com.lava.repository;

import static com.lava.model.database.Tables.PERMISSIONS;
import static com.lava.model.database.Tables.ROLES;
import static com.lava.model.database.Tables.ROLE_PERMISSIONS;
import static com.lava.model.database.Tables.USERS;
import static com.lava.model.database.Tables.USER_ROLES;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.selectDistinct;

import com.lava.model.database.tables.records.UsersRecord;
import com.lava.model.database.view.AuthUserView;
import com.lava.model.database.view.AuthUserViewBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean existsByEmail(String email) {
        return dsl.fetchExists(dsl.selectFrom(USERS).where(USERS.EMAIL.eq(normalize(email))));
    }

    public UsersRecord insert(String email, String passwordHash) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, normalize(email))
                .set(USERS.PASSWORD_HASH, passwordHash)
                .returning()
                .fetchOne();
    }

    // A single query: role/permission names are fetched as correlated MULTISET
    // subqueries alongside the user row, instead of three round-trips.
    public Optional<AuthUserView> findAuthUserByEmail(String email) {
        Field<List<String>> roles = multiset(selectDistinct(ROLES.NAME)
                        .from(USER_ROLES)
                        .join(ROLES)
                        .on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                        .where(USER_ROLES.USER_ID.eq(USERS.ID)))
                .as("roles")
                .convertFrom(r -> r.getValues(ROLES.NAME));

        Field<List<String>> permissions = multiset(selectDistinct(PERMISSIONS.NAME)
                        .from(USER_ROLES)
                        .join(ROLE_PERMISSIONS)
                        .on(ROLE_PERMISSIONS.ROLE_ID.eq(USER_ROLES.ROLE_ID))
                        .join(PERMISSIONS)
                        .on(PERMISSIONS.ID.eq(ROLE_PERMISSIONS.PERMISSION_ID))
                        .where(USER_ROLES.USER_ID.eq(USERS.ID)))
                .as("permissions")
                .convertFrom(r -> r.getValues(PERMISSIONS.NAME));

        return dsl.select(
                        USERS.ID,
                        USERS.EMAIL,
                        USERS.PASSWORD_HASH,
                        USERS.STATUS,
                        USERS.EMAIL_VERIFIED,
                        roles,
                        permissions)
                .from(USERS)
                .where(USERS.EMAIL.eq(normalize(email)))
                .fetchOptional(rec -> AuthUserViewBuilder.builder()
                        .id(rec.get(USERS.ID))
                        .email(rec.get(USERS.EMAIL))
                        .passwordHash(rec.get(USERS.PASSWORD_HASH))
                        .status(rec.get(USERS.STATUS))
                        .emailVerified(Boolean.TRUE.equals(rec.get(USERS.EMAIL_VERIFIED)))
                        .roles(rec.get(roles))
                        .permissions(rec.get(permissions))
                        .build());
    }

    // Emails are stored lower-cased so the plain UNIQUE constraint on the column
    // enforces case-insensitive uniqueness without needing CITEXT or a functional index.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
