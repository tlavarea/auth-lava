package com.lava.swexpedited.boot.autoconfigure.app;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credentials and endpoint for Samsara's fleet API. apiToken deliberately has no {@code @NotBlank} and no default -
 * unlike GFM's keystore/truststore, there's no {@code @Profile("!test")}-excluded bean construction to defer the
 * failure to, so validating it here would fail the *entire* application context at startup for any developer running
 * sw-expedited locally without a real Samsara token, not just the Samsara sync. A blank token instead flows through to
 * a real (401-ing) Samsara request the first time a sync job actually runs, which is the same "fail where the problem
 * actually is" behavior GFM gets from excluding its credential-dependent bean under the test profile.
 */
@ConfigurationProperties(prefix = "samsara")
@Validated
public record SamsaraProperties(String apiToken, @NotBlank String baseUrl) {}
