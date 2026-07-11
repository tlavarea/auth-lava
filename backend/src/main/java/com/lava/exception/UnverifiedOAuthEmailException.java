package com.lava.exception;

public class UnverifiedOAuthEmailException extends RuntimeException {

    public UnverifiedOAuthEmailException() {
        super("OAuth provider did not supply a verified email");
    }
}
