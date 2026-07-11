package com.lava.service;

public interface EmailService {

    /**
     * Sends the registration verification code to the given email address.
     *
     * @param email - the recipient.
     * @param code - the plaintext verification code (never persisted anywhere - the DB only ever sees its hash).
     */
    void sendVerificationCode(String email, String code);
}
