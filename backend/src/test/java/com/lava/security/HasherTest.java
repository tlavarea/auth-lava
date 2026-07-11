package com.lava.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HasherTest {

    private static final String KNOWN_SHA_256_HEX = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    void hash_defaultsToSha256() {
        assertThat(Hasher.hash("hello")).isEqualTo(KNOWN_SHA_256_HEX);
    }

    @Test
    void hash_withExplicitSha256Algorithm_matchesDefaultOverload() {
        assertThat(Hasher.hash("hello", HashAlgorithm.SHA_256)).isEqualTo(Hasher.hash("hello"));
    }

    @Test
    void matches_correctRawValue_returnsTrue() {
        assertThat(Hasher.matches("hello", KNOWN_SHA_256_HEX)).isTrue();
    }

    @Test
    void matches_wrongRawValue_returnsFalse() {
        assertThat(Hasher.matches("goodbye", KNOWN_SHA_256_HEX)).isFalse();
    }
}
