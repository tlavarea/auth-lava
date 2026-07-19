package com.lava.swexpedited.batch.vektor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Caches the JWT {@link VektorAuthenticator#authenticate()} returns, for the on-demand (live HTTP request-triggered)
 * Vektor calls - {@link VektorEntityLocationClient}/{@link VektorTruckEtaStatesClient} - rather than logging in fresh
 * on every call the way those would otherwise need to, which would mean re-authenticating with Vektor on literally
 * every 15-second live-marker poll. {@code VektorSyncTasklet}'s own login (once per ~20-minute batch run) is untouched
 * and independent of this - {@code VektorAuthenticator#authenticate} is stateless, so the two never interfere with each
 * other.
 *
 * <p>The cached JWT's real expiry is read directly from its own {@code exp} claim (a self-contained HS256 JWT - see
 * {@link VektorAuthenticator}'s javadoc) rather than guessed via a configured TTL - a real captured session was valid
 * for 90 days, far longer than any config default could safely assume, and reading the actual claim removes the
 * guesswork entirely. A 5-minute safety margin is subtracted so a call never starts against a token that expires
 * mid-flight. As a second line of defense (in case Vektor invalidates a session early - a password change, for
 * example), any caller that gets an auth-shaped failure from a live call should call {@link #invalidate()} and retry
 * once, rather than relying solely on the expiry check here.
 */
@Component
public class VektorSessionProvider {

    private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofMinutes(5);

    private final VektorAuthenticator vektorAuthenticator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CachedSession cachedSession;

    public VektorSessionProvider(VektorAuthenticator vektorAuthenticator) {
        this.vektorAuthenticator = vektorAuthenticator;
    }

    public synchronized String jwt() {
        if (this.cachedSession == null
                || Instant.now().isAfter(this.cachedSession.expiresAt().minus(EXPIRY_SAFETY_MARGIN))) {
            String jwt = this.vektorAuthenticator.authenticate();
            this.cachedSession = new CachedSession(jwt, expiryOf(jwt));
        }
        return this.cachedSession.jwt();
    }

    /** Forces the next {@link #jwt()} call to re-authenticate, rather than reuse a session a caller found rejected. */
    public synchronized void invalidate() {
        this.cachedSession = null;
    }

    /**
     * Runs {@code call} with the current cached JWT; if it fails with an auth-shaped error (an HTTP 4xx, or a gRPC-Web
     * trailer reporting a rejected/expired session - see {@link VektorClient#requireBody}), invalidates the cached
     * session and retries exactly once with a freshly authenticated JWT, rather than assuming the TTL-based expiry
     * check above always gets it right.
     */
    public <T> T withSession(Function<String, T> call) {
        try {
            return call.apply(jwt());
        } catch (HttpClientErrorException | VektorGrpcWeb.VektorGrpcWebException e) {
            invalidate();
            return call.apply(jwt());
        }
    }

    private Instant expiryOf(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Vektor JWT has no payload segment to read an expiry from");
        }
        String payload = parts[1];
        payload += "=".repeat((4 - payload.length() % 4) % 4);
        JsonNode claims;
        try {
            claims = this.objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decode Vektor JWT payload", e);
        }
        JsonNode exp = claims.get("exp");
        if (exp == null) {
            throw new IllegalStateException("Vektor JWT has no exp claim");
        }
        return Instant.ofEpochSecond(exp.asLong());
    }

    private record CachedSession(String jwt, Instant expiresAt) {}
}
