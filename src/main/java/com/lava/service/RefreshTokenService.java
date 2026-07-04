package com.lava.service;

import com.lava.model.auth.Issued;
import com.lava.model.database.tables.pojos.RefreshToken;
import java.util.Optional;

public interface RefreshTokenService {

    Optional<RefreshToken> findForLogout(String rawToken);

    Issued issue(Long userId);

    void revoke(Long refreshTokenId);

    void revokeAllForUser(Long userId);

    Issued rotate(RefreshToken old);

    RefreshToken validateForRotation(String rawToken);
}
