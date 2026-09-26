package com.lava.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lava.security.oauth.EnabledOAuthProviders;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthProviderControllerTest {

    @Mock
    private EnabledOAuthProviders enabledOAuthProviders;

    @Test
    @DisplayName("returns the enabled providers for the SPA to render buttons from")
    void returnsEnabledProviders() {
        when(enabledOAuthProviders.registrationIds()).thenReturn(List.of("apple", "google"));

        assertThat(new OAuthProviderController(enabledOAuthProviders).providers())
                .containsEntry("providers", List.of("apple", "google"));
    }
}
