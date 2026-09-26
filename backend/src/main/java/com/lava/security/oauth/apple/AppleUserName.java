package com.lava.security.oauth.apple;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the name Apple sends once and never again.
 *
 * <p>Apple puts the user's name in a {@code user} form field on the authorization callback, as JSON:
 * {@code {"name":{"firstName":"..","lastName":".."},"email":".."}}. It is present on the <b>first</b> authorization
 * only. Not on the second, not in the ID token, and not from any endpoint — Apple has no userinfo endpoint. A user who
 * signs in, is not recorded, and signs in again is nameless until they revoke the app in their Apple ID settings and
 * start over. Hence this being read at the callback rather than anywhere more comfortable.
 *
 * <p>The email in the same payload is deliberately ignored. The ID token carries it, signed, and that is the copy worth
 * having — this field is an unsigned form parameter and this service links accounts on verified email.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AppleUserName {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param userParameter - the raw {@code user} form field, or null when Apple sent none (i.e. every login after the
     *     first).
     * @return the name as one string, or empty if it was absent, unparseable or blank.
     */
    public static Optional<String> from(String userParameter) {
        if (StringUtils.isBlank(userParameter)) {
            return Optional.empty();
        }

        try {
            JsonNode name = JSON.readTree(userParameter).path("name");
            String full = StringUtils.normalizeSpace(name.path("firstName").asString("") + " "
                    + name.path("lastName").asString(""));
            return Optional.ofNullable(StringUtils.trimToNull(full));
        } catch (RuntimeException e) {
            // Unparseable is treated as absent, never as a failure: this is a cosmetic attribute arriving on the one
            // request that completes a sign-in, and losing a name is not a reason to refuse someone entry.
            log.warn("Could not read Apple's user parameter; continuing without a name", e);
            return Optional.empty();
        }
    }
}
