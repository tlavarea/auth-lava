package com.lava.model.throttle;

import java.util.Locale;

public enum AuthThrottleScope {
    LOGIN,
    MFA_VERIFY;

    public String dbValue() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
