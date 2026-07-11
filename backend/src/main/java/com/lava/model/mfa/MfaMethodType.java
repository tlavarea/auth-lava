package com.lava.model.mfa;

import java.util.Locale;

public enum MfaMethodType {
    TOTP;

    public String dbValue() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
