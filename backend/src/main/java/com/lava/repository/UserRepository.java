package com.lava.repository;

import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.view.AuthUserView;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<AuthUserView> findAuthUserByEmail(String email);

    Optional<AuthUserView> findAuthUserById(Long id);

    Optional<User> insert(String email, String passwordHash);

    Optional<User> insertVerified(String email, String passwordHash);

    Optional<User> insertVerifiedFromOAuth(String email, String displayName);

    /**
     * Writes a display name only if the user has none. Deliberately not an update: a provider's idea of someone's name
     * does not outrank a name already recorded, and this runs on every OAuth login, not just the first.
     *
     * <p>The "only if empty" test is in the SQL rather than a read-then-write, so two concurrent logins cannot both see
     * null and race.
     *
     * @param userId - the user to fill in.
     * @param displayName - the name the provider reported; ignored when blank.
     */
    void backfillDisplayName(Long userId, String displayName);

    void recordLogin(Long userId);

    void updatePasswordHash(Long userId, String passwordHash);

    /**
     * Updates the user's email and marks it verified - callers must only invoke this once ownership of the new address
     * has actually been proven (e.g. a verification code sent to it was entered correctly).
     *
     * @param userId - the user to update.
     * @param newEmail - the new email address.
     */
    void updateEmail(Long userId, String newEmail);
}
