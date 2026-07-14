package com.lava.controller;

import com.lava.service.JwtService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes auth-lava's RSA public key so other services can verify JWTs issued by this one without sharing the signing
 * key or hitting this service per-request. Deliberately unauthenticated - a JWKS document only ever contains public key
 * material.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtService jwtService;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) jwtService.getPublicKey())
                .keyID(jwtService.getKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();

        return new JWKSet(rsaKey).toJSONObject();
    }
}
