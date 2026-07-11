package com.lava.repository;

import static com.lava.model.database.Tables.ROLE;
import static com.lava.model.database.Tables.USER;
import static com.lava.model.database.Tables.USER_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.view.AuthUserView;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserRepository userRepository;

    @Test
    void existsByEmail_caseInsensitiveMatch() {
        this.userRepository.insert("Foo@Example.com", "hash");

        assertThat(this.userRepository.existsByEmail("foo@example.com")).isTrue();
        assertThat(this.userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void findAuthUserByEmail_notFound_isEmpty() {
        assertThat(this.userRepository.findAuthUserByEmail("nobody@example.com"))
                .isEmpty();
    }

    @Test
    void findAuthUserById_notFound_isEmpty() {
        assertThat(this.userRepository.findAuthUserById(-1L)).isEmpty();
    }

    @Test
    void findAuthUserByEmail_userWithNoRole_returnsEmptyRolesAndPermissions() {
        User user = this.userRepository.insert("norole@example.com", "hash").orElseThrow();

        Optional<AuthUserView> view = this.userRepository.findAuthUserById(user.id());

        assertThat(view).isPresent();
        assertThat(view.get().roles()).isEmpty();
        assertThat(view.get().permissions()).isEmpty();
    }

    @Test
    void findAuthUserByEmail_userWithMemberRole_returnsRoleAndItsPermissions() {
        User user = this.userRepository.insert("member@example.com", "hash").orElseThrow();
        Long memberRoleId = this.dsl
                .select(ROLE.ID)
                .from(ROLE)
                .where(ROLE.NAME.eq("member"))
                .fetchOne(ROLE.ID);
        this.dsl
                .insertInto(USER_ROLE)
                .set(USER_ROLE.USER_ID, user.id())
                .set(USER_ROLE.ROLE_ID, memberRoleId)
                .execute();

        Optional<AuthUserView> view = this.userRepository.findAuthUserByEmail("member@example.com");

        assertThat(view).isPresent();
        assertThat(view.get().roles()).containsExactly("member");
        assertThat(view.get().permissions()).containsExactly("users:read");
    }

    @Test
    void insert_normalizesEmailToLowercaseTrimmed() {
        User user =
                this.userRepository.insert("  Mixed@Case.COM  ".trim(), "hash").orElseThrow();

        assertThat(user.email()).isEqualTo("mixed@case.com");
    }

    @Test
    void insert_duplicateEmail_throwsDataAccessException() {
        this.userRepository.insert("dup@example.com", "hash");

        assertThatThrownBy(() -> this.userRepository.insert("dup@example.com", "hash2"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void insertVerifiedFromOAuth_setsEmailVerifiedTrue() {
        User user =
                this.userRepository.insertVerifiedFromOAuth("oauth@example.com").orElseThrow();

        assertThat(user.emailVerified()).isTrue();
    }

    @Test
    void recordLogin_setsLastLoginAt() {
        User user = this.userRepository.insert("login@example.com", "hash").orElseThrow();
        assertThat(user.lastLoginAt()).isNull();

        this.userRepository.recordLogin(user.id());

        LocalDateTime lastLoginAt = this.dsl
                .select(USER.LAST_LOGIN_AT)
                .from(USER)
                .where(USER.ID.eq(user.id()))
                .fetchOne(USER.LAST_LOGIN_AT);
        assertThat(lastLoginAt).isNotNull();
    }
}
