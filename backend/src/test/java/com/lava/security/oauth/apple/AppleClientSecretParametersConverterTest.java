package com.lava.security.oauth.apple;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.boot.autoconfigure.app.AppleProperties;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.util.MultiValueMap;

class AppleClientSecretParametersConverterTest {

    private static final String SERVICES_ID = "com.platetune.web";

    private static KeyPair keyPair;
    private AppleClientSecretParametersConverter converter;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @Test
    @DisplayName("sends the minted JWT as client_secret, not as a client_assertion")
    void sendsTheJwtAsClientSecret() {
        MultiValueMap<String, String> parameters = converter().convert(grantRequest("apple", SERVICES_ID));

        assertThat(parameters).isNotNull();
        // The distinction that costs a day: Apple's is RFC-7523-shaped but goes in the ordinary secret parameter.
        assertThat(parameters.keySet()).containsExactly("client_secret");
        assertThat(parameters).doesNotContainKey("client_assertion");
    }

    @Test
    @DisplayName("signs it for the Services ID on the registration, so one key serves several clients")
    void signsForTheRegistrationsClientId() {
        String secret = converter().convert(grantRequest("apple", SERVICES_ID)).getFirst("client_secret");

        var claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(secret)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(SERVICES_ID);
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");
    }

    @Test
    @DisplayName("leaves every other provider's token request untouched")
    void ignoresOtherRegistrations() {
        assertThat(converter().convert(grantRequest("google", "a-google-client-id")))
                .isNull();
    }

    private AppleClientSecretParametersConverter converter() {
        if (converter == null) {
            String base64Der =
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            converter = new AppleClientSecretParametersConverter(new AppleClientSecretFactory(
                    new AppleProperties("A1B2C3D4E5", "K9Y1D2E3F4", base64Der, Duration.ofMinutes(5))));
        }
        return converter;
    }

    private OAuth2AuthorizationCodeGrantRequest grantRequest(String registrationId, String clientId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://auth.platetune.com/login/oauth2/code/" + registrationId)
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token")
                .build();

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(clientId)
                .redirectUri(registration.getRedirectUri())
                .state("state")
                .build();

        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("a-code")
                .redirectUri(registration.getRedirectUri())
                .state("state")
                .build();

        return new OAuth2AuthorizationCodeGrantRequest(
                registration, new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse));
    }
}
