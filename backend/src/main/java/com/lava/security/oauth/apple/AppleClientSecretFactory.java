package com.lava.security.oauth.apple;

import com.lava.boot.autoconfigure.app.AppleProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Mints the client secret Apple expects: an ES256 JWT, not a static string.
 *
 * <p>This is the one genuinely non-standard part of Sign in with Apple. Apple's token endpoint takes the ordinary
 * {@code client_secret} form parameter, but the value must be a JWT signed with the private key downloaded from the
 * Developer portal. It is <em>similar to</em> RFC 7523 but is not it — configuring Spring Security's
 * {@code private_key_jwt} client authentication instead sends {@code client_assertion}/{@code client_assertion_type},
 * which Apple answers with {@code invalid_client}.
 *
 * <p>Claims per Apple's spec: {@code iss} is the Team ID, {@code sub} the Services ID (which is the registration's
 * client id), {@code aud} always {@code https://appleid.apple.com}, and the header carries the Key ID as {@code kid}.
 *
 * <p>Minted per request rather than cached. Apple allows up to six months, but a token exchange happens once per login
 * and signing an ES256 JWT costs microseconds — there is nothing to gain by keeping a long-lived credential in memory.
 */
@Component
public class AppleClientSecretFactory {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final String EC_ALGORITHM = "EC";

    private final AppleProperties properties;
    private final PrivateKey privateKey;
    private final Clock clock;

    public AppleClientSecretFactory(AppleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AppleClientSecretFactory(AppleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.privateKey = decodePrivateKey(properties.privateKey());
    }

    /**
     * @param clientId - the Services ID, taken from the client registration rather than configured twice.
     * @return a signed ES256 JWT to send as {@code client_secret}.
     */
    public String createClientSecret(String clientId) {
        Instant now = clock.instant();

        return Jwts.builder()
                .header()
                .keyId(properties.keyId())
                .and()
                .issuer(properties.teamId())
                .subject(clientId)
                .audience()
                .add(APPLE_AUDIENCE)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.clientSecretTtl())))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    /**
     * Apple hands out a PEM {@code .p8}; this wants the same shape as every other key in this service — base64 of the
     * PKCS#8 DER, no armour. The conversion is {@code openssl pkcs8 -topk8 -nocrypt -in AuthKey_X.p8 -outform DER |
     * base64 -w0}.
     */
    private static PrivateKey decodePrivateKey(String base64) {
        try {
            return KeyFactory.getInstance(EC_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(Decoders.BASE64.decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "apple.private-key is not a valid EC PKCS8 key. Apple issues a PEM .p8; convert it with"
                            + " `openssl pkcs8 -topk8 -nocrypt -in AuthKey_<keyId>.p8 -outform DER | base64 -w0`.",
                    e);
        }
    }
}
