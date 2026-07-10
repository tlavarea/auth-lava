package com.lava.exception;

public class BreachedPasswordException extends RuntimeException {

    public BreachedPasswordException() {
        super("This password has appeared in a data breach - please choose a different password");
    }
}
