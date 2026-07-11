package com.lava.exception;

public class MfaAlreadyEnabledException extends RuntimeException {

    public MfaAlreadyEnabledException() {
        super("MFA is already enabled for this account");
    }
}
