package com.lava.security.oauth.apple;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppleUserNameTest {

    @Test
    @DisplayName("reads the name out of the payload Apple posts on a first authorization")
    void readsBothNames() {
        String payload = """
                {"name":{"firstName":"Tim","lastName":"Lavarea"},"email":"tim@privaterelay.appleid.com"}""";

        assertThat(AppleUserName.from(payload)).contains("Tim Lavarea");
    }

    @Test
    @DisplayName("copes with only one of the two names, without a stray space")
    void readsAPartialName() {
        assertThat(AppleUserName.from("{\"name\":{\"firstName\":\"Prince\"}}")).contains("Prince");
        assertThat(AppleUserName.from("{\"name\":{\"lastName\":\"Pelé\"}}")).contains("Pelé");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                // Every login after the first: Apple sends the parameter with no name in it, or not at all.
                "{}",
                "{\"email\":\"tim@privaterelay.appleid.com\"}",
                "{\"name\":{}}",
                "{\"name\":{\"firstName\":\"\",\"lastName\":\"  \"}}",
                // Malformed is absent, not fatal - a name is never worth failing a sign-in over.
                "not json at all",
                "{\"name\":",
            })
    @DisplayName("treats anything without a usable name as no name, never as an error")
    void missingOrUnusableIsEmpty(String payload) {
        assertThat(AppleUserName.from(payload)).isEmpty();
    }

    @Test
    @DisplayName("ignores the email in the payload - the signed copy in the ID token is the one that counts")
    void ignoresTheEmail() {
        assertThat(AppleUserName.from("{\"email\":\"someone@else.example\"}")).isEmpty();
    }
}
