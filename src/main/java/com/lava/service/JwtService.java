package com.lava.service;

import com.lava.security.AuthUserPrincipal;
import io.jsonwebtoken.Claims;

public interface JwtService {

    String generateAccessToken(AuthUserPrincipal principal);

    long getAccessTokenTtlSeconds();

    Claims parseAndValidate(String token);
}
