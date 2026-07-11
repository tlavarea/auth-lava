package com.lava.repository;

import com.lava.model.database.tables.pojos.OauthAccount;
import java.util.Optional;

public interface OauthAccountRepository {

    Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    OauthAccount insert(Long userId, String provider, String providerUserId);
}
