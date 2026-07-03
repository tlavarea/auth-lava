package com.lava.logging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.owasp.encoder.Encode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogSanitizer {

    public static String sanitize(Object value) {
        return value == null ? "null" : Encode.forJava(value.toString());
    }
}
