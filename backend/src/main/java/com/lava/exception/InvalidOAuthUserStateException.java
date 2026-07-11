package com.lava.exception;

public class InvalidOAuthUserStateException extends RuntimeException {

    public InvalidOAuthUserStateException() {
        super("Account is not active");
    }
}
