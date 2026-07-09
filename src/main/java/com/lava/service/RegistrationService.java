package com.lava.service;

public interface RegistrationService {

    /**
     * Final step: creates the verified user account from a bridge token issued by {@link #verifyCode}.
     *
     * @param rawRegistrationToken - the bridge token returned by {@link #verifyCode}.
     * @param rawPassword - the password the user chose.
     */
    void complete(String rawRegistrationToken, String rawPassword);

    /**
     * First step (and also how a code is resent): generates and emails a verification code for the given email,
     * overwriting any code already pending for it.
     *
     * @param email - the email address being registered.
     */
    void start(String email);

    /**
     * Second step: validates the emailed code and, on success, returns a short-lived bridge token that authorizes
     * {@link #complete} to create the account.
     *
     * @param email - the email address being registered.
     * @param code - the code the user entered.
     * @return the bridge token.
     */
    String verifyCode(String email, String code);
}
