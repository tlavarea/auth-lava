package com.lava.security.oauth.apple;

import com.lava.boot.autoconfigure.app.AppleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;

/**
 * Everything Sign in with Apple needs beyond the registration in {@code application-oauth-apple.yaml}.
 *
 * <p>Profile-gated as a whole, for the reason {@code EnabledOAuthProviders} explains for providers generally: a
 * deployment that has not configured Apple should not have Apple beans, and asking for them by activating the profile
 * is what makes a missing key a startup failure rather than a broken button. Neither class below carries
 * {@code @Component} — as scanned components they would be created in every deployment, and
 * {@link AppleClientSecretParametersConverter} would additionally be swept up as a generic {@code Converter} bean by
 * every {@code @WebMvcTest} slice in the test suite.
 *
 * <p>The token response client is not wired into {@code SecurityConfiguration}. {@code OAuth2LoginConfigurer} resolves
 * an {@code OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>} from the application context if one
 * is there and falls back to its own otherwise, so publishing the bean here is the whole wiring — and it means the
 * security configuration stays free of any mention of a provider that may not exist.
 */
@Configuration
@Profile("oauth-apple")
public class AppleOAuthConfiguration {

    @Bean
    public AppleClientSecretFactory appleClientSecretFactory(AppleProperties appleProperties) {
        return new AppleClientSecretFactory(appleProperties);
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient(
            AppleClientSecretFactory appleClientSecretFactory) {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setParametersConverter(new AppleClientSecretParametersConverter(appleClientSecretFactory));
        return client;
    }
}
