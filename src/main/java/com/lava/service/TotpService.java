package com.lava.service;

public interface TotpService {

    String buildOtpAuthUri(String accountEmail, String secret);

    String generateQrCodeDataUri(String accountEmail, String secret);

    String generateSecret();

    boolean verifyCode(String secret, String submittedCode);
}
