package com.lava.security;

public enum HashAlgorithm {
    SHA_256("SHA-256"),

    // Used only for the HaveIBeenPwned k-anonymity lookup protocol (PasswordBreachCheckServiceImpl) -
    // unrelated to how this app stores/verifies passwords, which uses Argon2 (see EncoderConfiguration).
    SHA_1("SHA-1");

    private final String jdkName;

    HashAlgorithm(String jdkName) {
        this.jdkName = jdkName;
    }

    public String jdkName() {
        return this.jdkName;
    }
}
