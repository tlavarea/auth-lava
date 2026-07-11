package com.lava.service;

public interface PasswordBreachCheckService {

    /**
     * Checks whether the given raw password appears in a known password-breach corpus.
     *
     * @param rawPassword - the raw password to check.
     * @return true if the password is known to have appeared in a breach.
     */
    boolean isBreached(String rawPassword);
}
