package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lava.swexpedited.vektor.VektorGrpcWeb;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class VektorSessionProviderTest {

    @Mock
    private VektorAuthenticator vektorAuthenticator;

    @Test
    void jwt_firstCall_authenticatesAndCachesTheJwt() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        String jwt = provider.jwt();

        assertThat(jwt).isNotBlank();
        verify(this.vektorAuthenticator).authenticate();
    }

    @Test
    void jwt_calledAgainBeforeExpiry_reusesTheCachedJwtWithoutReauthenticating() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        String first = provider.jwt();
        String second = provider.jwt();

        assertThat(second).isEqualTo(first);
        verify(this.vektorAuthenticator).authenticate();
    }

    @Test
    void jwt_cachedSessionWithinSafetyMarginOfExpiry_reauthenticates() {
        // 2 minutes out is inside the 5-minute safety margin, so this should be treated as already-expired.
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(120)))
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        provider.jwt();
        provider.jwt();

        verify(this.vektorAuthenticator, times(2)).authenticate();
    }

    @Test
    void invalidate_forcesReauthenticationOnNextCallEvenIfNotExpired() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)))
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);
        provider.jwt();

        provider.invalidate();
        provider.jwt();

        verify(this.vektorAuthenticator, times(2)).authenticate();
    }

    @Test
    void jwt_tokenWithoutExpClaim_throws() {
        when(this.vektorAuthenticator.authenticate()).thenReturn(fakeJwtWithoutExp());
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        assertThatThrownBy(provider::jwt).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withSession_callThrowsAuthShapedError_invalidatesAndRetriesOnceThenSucceeds() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)))
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);
        AtomicInteger attempts = new AtomicInteger();

        String result = provider.withSession(jwt -> {
            if (attempts.getAndIncrement() == 0) {
                throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);
            }
            return jwt;
        });

        assertThat(result).isNotBlank();
        assertThat(attempts.get()).isEqualTo(2);
        verify(this.vektorAuthenticator, times(2)).authenticate();
    }

    @Test
    void withSession_callThrowsAuthShapedErrorTwice_propagatesAfterOneRetry() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        assertThatThrownBy(() -> provider.withSession(jwt -> {
                    throw new VektorGrpcWeb.VektorGrpcWebException("rejected");
                }))
                .isInstanceOf(VektorGrpcWeb.VektorGrpcWebException.class);
        verify(this.vektorAuthenticator, times(2)).authenticate();
    }

    @Test
    void withSession_callSucceeds_doesNotReauthenticate() {
        when(this.vektorAuthenticator.authenticate())
                .thenReturn(fakeJwt(Instant.now().plusSeconds(3600)));
        VektorSessionProvider provider = new VektorSessionProvider(this.vektorAuthenticator);

        String result = provider.withSession(jwt -> jwt);

        assertThat(result).isNotBlank();
        verify(this.vektorAuthenticator).authenticate();
    }

    /** A minimal, unsigned JWT with just the claim(s) {@link VektorSessionProvider} actually reads. */
    private String fakeJwt(Instant expiresAt) {
        String payload = "{\"exp\":" + expiresAt.getEpochSecond() + "}";
        return base64UrlSegment("{}") + "." + base64UrlSegment(payload) + ".signature";
    }

    private String fakeJwtWithoutExp() {
        return base64UrlSegment("{}") + "." + base64UrlSegment("{}") + ".signature";
    }

    private String base64UrlSegment(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
