package com.lava.service;

import com.lava.boot.autoconfigure.app.PasswordBreachProperties;
import com.lava.logging.LogSanitizer;
import com.lava.security.HashAlgorithm;
import com.lava.security.Hasher;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Checks a raw password against the HaveIBeenPwned Pwned Passwords k-anonymity API: only the first 5 hex characters of
 * a SHA-1 hash of the password ever leave this server, never the password, the full hash, or a suffix - the remaining
 * 35 characters are compared locally against every candidate the API returns for that prefix. This SHA-1 use is
 * specific to that protocol and unrelated to how this app actually stores/verifies passwords (Argon2, see
 * EncoderConfiguration).
 *
 * <p>Fails open: any failure calling the API (timeout, 5xx, network error) is logged and treated as "not breached"
 * rather than blocking registration on a third-party outage, mirroring
 * {@link com.lava.security.oauth.GithubEmailBackfillOAuth2UserService}'s handling of its own external call.
 *
 * <p>Deliberately builds the injected {@code RestClient.Builder} as-is, with no per-service {@code requestFactory}
 * override - a request timeout for this call (and every other auto-configured HTTP client) is set globally via
 * {@code spring.http.clients.connect-timeout}/{@code read-timeout} in application.yaml instead. Overriding the request
 * factory here would silently replace whatever {@code MockRestServiceServer} had already bound onto the builder in
 * tests, since both mechanisms work by mutating the same builder's request factory and the later call always wins -
 * tests would stop hitting the mock and start making real network calls.
 */
@Service
@Slf4j
public class PasswordBreachCheckServiceImpl implements PasswordBreachCheckService {

    private static final String PWNED_PASSWORDS_RANGE_URI = "https://api.pwnedpasswords.com/range/{prefix}";

    private final PasswordBreachProperties properties;
    private final RestClient restClient;

    public PasswordBreachCheckServiceImpl(RestClient.Builder restClientBuilder, PasswordBreachProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isBreached(String rawPassword) {
        if (!this.properties.enabled()) {
            return false;
        }

        try {
            String sha1Hex = Hasher.hash(rawPassword, HashAlgorithm.SHA_1).toUpperCase(Locale.ROOT);
            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            String response = this.restClient
                    .get()
                    .uri(PWNED_PASSWORDS_RANGE_URI, prefix)
                    // Pads the response with decoy entries so its size can't be used to infer whether this
                    // specific hash had zero vs nonzero hits.
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);

            return response != null
                    && response.lines().map(line -> line.split(":", 2)[0]).anyMatch(suffix::equalsIgnoreCase);
        } catch (RuntimeException e) {
            log.warn(
                    "isBreached::failed to check password breach status: {}",
                    LogSanitizer.sanitize(ExceptionUtils.getMessage(e)),
                    e);
            return false;
        }
    }
}
