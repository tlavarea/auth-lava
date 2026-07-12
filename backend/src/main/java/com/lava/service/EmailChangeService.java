package com.lava.service;

public interface EmailChangeService {

    /**
     * Starts an email change: sends a verification code to {@code newEmail}. Silently no-ops if that address already
     * belongs to a user, to avoid letting this authenticated endpoint be used to enumerate other accounts' emails.
     *
     * @param userId - the user requesting the change.
     * @param newEmail - the email address being switched to.
     */
    void start(Long userId, String newEmail);

    /**
     * Confirms a pending email change: on a correct, unexpired code, commits {@code new_email} onto the user row
     * (marking it verified, since receiving the code at that address is itself proof of ownership) and clears the
     * pending row.
     *
     * @param userId - the user confirming the change.
     * @param code - the plaintext code entered by the user.
     * @return the new email address now on the user's account.
     */
    String confirm(Long userId, String code);
}
