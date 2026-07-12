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

    Optional<User> insertVerifiedFromOAuth(String email);

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
