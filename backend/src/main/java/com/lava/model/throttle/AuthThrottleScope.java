package com.lava.model.throttle;

import java.util.Locale;

public enum AuthThrottleScope {
    LOGIN,
    MFA_VERIFY,
    PASSWORD_CHANGE;

    public String dbValue() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
