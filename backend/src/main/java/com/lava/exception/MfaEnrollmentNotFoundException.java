package com.lava.exception;

public class MfaEnrollmentNotFoundException extends RuntimeException {

    public MfaEnrollmentNotFoundException() {
        super("No pending MFA enrollment found");
    }
}
