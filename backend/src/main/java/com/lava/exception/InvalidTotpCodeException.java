package com.lava.exception;

public class InvalidTotpCodeException extends RuntimeException {

    public InvalidTotpCodeException() {
        super("Invalid verification code");
    }
}
