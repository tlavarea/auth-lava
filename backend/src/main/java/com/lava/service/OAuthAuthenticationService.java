package com.lava.service;

import com.lava.model.auth.TokenPair;
import com.lava.security.oauth.OAuthIdentity;

public interface OAuthAuthenticationService {

    TokenPair authenticate(OAuthIdentity identity);
}
