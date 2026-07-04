package com.lava.service;

import com.lava.model.auth.TokenPair;
import com.lava.security.AuthUserPrincipal;
import java.util.Optional;

public interface AuthService {

    TokenPair login(String email, String rawPassword);

    void logout(AuthUserPrincipal principal, Optional<String> rawRefreshToken);

    TokenPair refresh(String rawRefreshToken);

    void register(String email, String rawPassword);
}
