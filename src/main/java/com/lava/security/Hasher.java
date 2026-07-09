package com.lava.security;

import com.lava.logging.LogSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class Hasher {

    /**
     * Hashes the given value with SHA-256, for use anywhere a secret (refresh token, backup code, etc.) needs to be
     * stored or looked up without persisting its raw form.
     *
     * @param rawValue - the value to hash.
     * @return the value as a SHA-256 hex digest.
     */
    public static String hash(String rawValue) {
        return hash(rawValue, HashAlgorithm.SHA_256);
    }

    /**
     * Hashes the given value with the given algorithm.
     *
     * @param rawValue - the value to hash.
     * @param algorithm - the digest algorithm to use.
     * @return the value as a hex digest.
     */
    public static String hash(String rawValue, HashAlgorithm algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.jdkName());
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            log.error("hash::error: {}", LogSanitizer.sanitize(ExceptionUtils.getMessage(e)), e);
            throw new IllegalStateException(algorithm.jdkName() + " not available", e);
        }
    }
}
