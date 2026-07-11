package com.lava.exception;

public class InvalidRegistrationTokenException extends RuntimeException {

    public InvalidRegistrationTokenException() {
        super("Invalid or expired registration session");
    }
}
