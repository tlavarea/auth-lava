package com.lava.service;

import com.lava.model.auth.TokenPair;
import com.lava.security.AuthUserPrincipal;
import java.util.Optional;

public interface AuthService {

    String completeMfaVerification(AuthUserPrincipal principal, String rawRefreshToken, String code);

    TokenPair login(String email, String rawPassword);

    void logout(AuthUserPrincipal principal, Optional<String> rawRefreshToken);

    TokenPair refresh(String rawRefreshToken);

    void register(String email, String rawPassword);
}
