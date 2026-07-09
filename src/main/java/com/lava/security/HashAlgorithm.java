package com.lava.security;

public enum HashAlgorithm {
    SHA_256("SHA-256");

    private final String jdkName;

    HashAlgorithm(String jdkName) {
        this.jdkName = jdkName;
    }

    public String jdkName() {
        return this.jdkName;
    }
}
