package com.lava.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class EnabledOAuthProvidersTest {

    @Test
    @DisplayName("reports the configured registration ids, sorted")
    void reportsConfiguredIds() {
        EnabledOAuthProviders providers = new EnabledOAuthProviders(
                provider(new InMemoryClientRegistrationRepository(registration("google"), registration("apple"))));

        assertThat(providers.registrationIds()).containsExactly("apple", "google");
    }

    @Test
    @DisplayName("fails startup when no provider profile is active at all")
    void failsWhenRepositoryAbsent() {
        // Spring Boot only auto-configures a ClientRegistrationRepository when at least one
        // registration is declared, so "no profiles active" shows up here as no bean.
        assertThatThrownBy(() -> new EnabledOAuthProviders(provider(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No OAuth providers are configured");
    }

    @Test
    @DisplayName("fails startup rather than publishing a partial list it cannot enumerate")
    void failsWhenRepositoryNotEnumerable() {
        ClientRegistrationRepository opaque = registrationId -> registration(registrationId);

        assertThatThrownBy(() -> new EnabledOAuthProviders(provider(opaque)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No OAuth providers are configured");
    }

    private static ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
                .clientId(id + "-client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token")
                .userInfoUri("https://example.test/userinfo")
                .userNameAttributeName("sub")
                .scope(List.of("openid"))
                .build();
    }

    private static ObjectProvider<ClientRegistrationRepository> provider(ClientRegistrationRepository repository) {
        return new ObjectProvider<>() {
            @Override
            public ClientRegistrationRepository getIfAvailable() {
                return repository;
            }

            @Override
            public ClientRegistrationRepository getObject() {
                return repository;
            }

            @Override
            public ClientRegistrationRepository getObject(Object... args) {
                return repository;
            }

            @Override
            public ClientRegistrationRepository getIfUnique() {
                return repository;
            }
        };
    }
}
