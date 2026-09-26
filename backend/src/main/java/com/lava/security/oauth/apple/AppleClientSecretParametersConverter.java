package com.lava.security.oauth.apple;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Substitutes a freshly minted JWT for Apple's {@code client_secret} on the token request.
 *
 * <p>Registered with {@code addParametersConverter} on the token response client, so it merges into the standard
 * parameters rather than replacing them — everything else about the authorization-code exchange is untouched. Returning
 * null for any other registration leaves Google and GitHub entirely alone.
 *
 * <p>Note this is the Spring Security 7 shape. Older guidance describes an
 * {@code OAuth2AuthorizationCodeGrantRequestEntityConverter} on {@code DefaultAuthorizationCodeTokenResponseClient};
 * both were removed in favour of {@code RestClientAuthorizationCodeTokenResponseClient} and converters like this one.
 */
@Component
public class AppleClientSecretParametersConverter
        implements Converter<OAuth2AuthorizationCodeGrantRequest, MultiValueMap<String, String>> {

    static final String APPLE_REGISTRATION_ID = "apple";

    private final AppleClientSecretFactory clientSecretFactory;

    public AppleClientSecretParametersConverter(AppleClientSecretFactory clientSecretFactory) {
        this.clientSecretFactory = clientSecretFactory;
    }

    @Override
    public MultiValueMap<String, String> convert(OAuth2AuthorizationCodeGrantRequest request) {
        var registration = request.getClientRegistration();

        if (!APPLE_REGISTRATION_ID.equals(registration.getRegistrationId())) {
            return null;
        }

        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.set(
                OAuth2ParameterNames.CLIENT_SECRET, clientSecretFactory.createClientSecret(registration.getClientId()));
        return parameters;
    }
}
