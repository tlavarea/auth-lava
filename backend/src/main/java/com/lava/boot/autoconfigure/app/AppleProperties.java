package com.lava.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sign in with Apple. Bound only when the {@code oauth-apple} profile is active.
 *
 * <p>Apple issues no static client secret. The secret is an ES256 JWT this service mints per token request, signed with
 * a private key downloaded once from the Apple Developer portal — see {@code AppleClientSecretFactory}.
 *
 * @param teamId - the Apple Developer Team ID, ten characters. Becomes the JWT's {@code iss}.
 * @param keyId - the Key ID of the Sign in with Apple key, from the downloaded {@code AuthKey_<keyId>.p8} filename.
 *     Becomes the JWT header's {@code kid}.
 * @param privateKey - the key itself: base64 of the PKCS#8 DER, with no PEM armour, matching how the RSA keys in
 *     {@link JwtProperties} are supplied.
 * @param clientSecretTtl - how long each minted secret is valid. Apple's ceiling is six months, but nothing here needs
 *     a long-lived one: it is generated per token request, so a short life narrows what a leaked assertion is worth.
 */
@ConfigurationProperties(prefix = "apple")
@Validated
public record AppleProperties(
        @NotBlank String teamId,
        @NotBlank String keyId,
        @NotBlank String privateKey,
        Duration clientSecretTtl) {

    public AppleProperties {
        clientSecretTtl = clientSecretTtl == null ? Duration.ofMinutes(5) : clientSecretTtl;
    }
}
