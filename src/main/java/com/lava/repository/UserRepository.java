package com.lava.repository;

import com.lava.model.database.tables.pojos.User;
import com.lava.model.database.view.AuthUserView;
import java.util.Optional;

public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<AuthUserView> findAuthUserByEmail(String email);

    Optional<AuthUserView> findAuthUserById(Long id);

    Optional<User> insert(String email, String passwordHash);

    Optional<User> insertVerifiedFromOAuth(String email);
}
